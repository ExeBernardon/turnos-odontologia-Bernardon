package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.horario.HorarioAtencion;
import ar.turnos.odontologia.horario.HorarioAtencionRepository;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.paciente.PacienteRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;
import ar.turnos.odontologia.turno.EstadoTurno;
import ar.turnos.odontologia.turno.Turno;
import ar.turnos.odontologia.turno.TurnoManualRequest;
import ar.turnos.odontologia.turno.TurnoRepository;
import ar.turnos.odontologia.turno.TurnoService;

/** Ciclo de avisos de punta a punta contra PostgreSQL real, con {@code NotificacionTurnos} simulado. */
@IntegracionPostgres
@Import(AvisosProgramadosIT.RelojFijo.class)
class AvisosProgramadosIT {

    /** Lunes 09:00 en Buenos Aires. */
    static final Instant AHORA = Instant.parse("2026-12-07T12:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class RelojFijo {
        @Bean
        @Primary
        Clock clockFijo() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

    @MockitoBean
    private NotificacionTurnos notificaciones;

    @Autowired
    private AvisosProgramadosService servicio;
    @Autowired
    private TurnoService turnoService;
    @Autowired
    private TurnoRepository turnos;
    @Autowired
    private PacienteRepository pacientes;
    @Autowired
    private PrestacionRepository prestaciones;
    @Autowired
    private HorarioAtencionRepository horarios;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationContext contexto;

    private Paciente paciente;
    private Prestacion prestacion;
    private int secuencia;

    @BeforeEach
    void preparar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre = 'Avisos IT'");
        // Mismo horario que el seed (lunes a viernes 09-13 y 15-19) para poder reprogramar y crear turnos.
        jdbc.update("DELETE FROM horario_atencion");
        for (DayOfWeek dia : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            horarios.save(new HorarioAtencion(dia, LocalTime.of(9, 0), LocalTime.of(13, 0)));
            horarios.save(new HorarioAtencion(dia, LocalTime.of(15, 0), LocalTime.of(19, 0)));
        }
        paciente = pacientes.saveAndFlush(new Paciente("Ana", "Ejemplo", "99999999"));
        prestacion = prestaciones.saveAndFlush(new Prestacion("Avisos IT", 30));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre = 'Avisos IT'");
    }

    private static Instant en(long horas) {
        return AHORA.plus(Duration.ofHours(horas));
    }

    private Turno guardar(Instant inicio, EstadoTurno estado, boolean recordatorio, boolean alerta) {
        Turno turno = new Turno(paciente, prestacion, inicio, inicio.plus(Duration.ofMinutes(30)), estado,
                "token-ficticio-avisos-" + (++secuencia), "paciente@ejemplo.com", "1155555555");
        turno.setRecordatorioEnviado(recordatorio);
        turno.setAlertaPendienteEnviada(alerta);
        return turnos.saveAndFlush(turno);
    }

    private Turno pendiente(Instant inicio) {
        return guardar(inicio, EstadoTurno.PENDIENTE, false, false);
    }

    private Turno releer(Turno turno) {
        return turnos.findById(turno.getId()).orElseThrow();
    }

    private static Turno turnoCon(Long id) {
        return argThat(t -> t != null && id.equals(t.getId()));
    }

    private void actualizarDesdeOtraConexion(String sql, Object... args) {
        // Otro hilo = otra conexion, fuera de la transaccion del ciclo.
        CompletableFuture.runAsync(() -> jdbc.update(sql, args)).join();
    }

    // --- 5.1 ---

    @Test
    void lasDosTareasCorrenEnElMismoCiclo() {
        Turno paraRecordatorio = pendiente(en(30));
        Turno paraAlerta = guardar(en(10), EstadoTurno.PENDIENTE, true, false);
        when(notificaciones.recordatorio(any())).thenReturn(true);
        when(notificaciones.turnoNoConfirmado(any())).thenReturn(true);

        ResultadoCiclo resultado = servicio.ejecutarCiclo(AHORA);

        verify(notificaciones).recordatorio(turnoCon(paraRecordatorio.getId()));
        verify(notificaciones).turnoNoConfirmado(turnoCon(paraAlerta.getId()));
        assertThat(resultado).isEqualTo(new ResultadoCiclo(1, 0, 1, 0));
        assertThat(releer(paraRecordatorio).isRecordatorioEnviado()).isTrue();
        assertThat(releer(paraAlerta).isAlertaPendienteEnviada()).isTrue();
    }

    @Test
    void bordesDe48HY24HContraPostgres() {
        Turno enElBordeDe48 = pendiente(en(48));
        Turno pasadoDe48 = pendiente(en(48).plus(Duration.ofMinutes(45)));
        Turno enElBordeDe24 = guardar(en(24), EstadoTurno.PENDIENTE, true, false);
        Turno dentroDe24 = guardar(en(24).minusSeconds(60).minus(Duration.ofMinutes(30)),
                EstadoTurno.PENDIENTE, true, false);
        when(notificaciones.recordatorio(any())).thenReturn(true);
        when(notificaciones.turnoNoConfirmado(any())).thenReturn(true);

        servicio.ejecutarCiclo(AHORA);

        assertThat(releer(enElBordeDe48).isRecordatorioEnviado()).isTrue();
        assertThat(releer(pasadoDe48).isRecordatorioEnviado()).isFalse();
        assertThat(releer(enElBordeDe24).isAlertaPendienteEnviada()).isFalse();
        assertThat(releer(dentroDe24).isAlertaPendienteEnviada()).isTrue();
    }

    @Test
    void unSegundoCicloNoReenviaNada() {
        Turno turno = pendiente(en(30));
        Turno paraAlerta = guardar(en(10), EstadoTurno.PENDIENTE, true, false);
        when(notificaciones.recordatorio(any())).thenReturn(true);
        when(notificaciones.turnoNoConfirmado(any())).thenReturn(true);

        servicio.ejecutarCiclo(AHORA);
        ResultadoCiclo segundo = servicio.ejecutarCiclo(AHORA);

        verify(notificaciones, times(1)).recordatorio(turnoCon(turno.getId()));
        verify(notificaciones, times(1)).turnoNoConfirmado(turnoCon(paraAlerta.getId()));
        assertThat(segundo).isEqualTo(new ResultadoCiclo(0, 0, 0, 0));
    }

    @Test
    void elRecordatorioQueFallaSeReintentaEnElCicloSiguiente() {
        Turno turno = pendiente(en(30));
        when(notificaciones.recordatorio(any())).thenReturn(false, true);

        servicio.ejecutarCiclo(AHORA);
        assertThat(releer(turno).isRecordatorioEnviado()).isFalse();

        servicio.ejecutarCiclo(AHORA);
        assertThat(releer(turno).isRecordatorioEnviado()).isTrue();
        verify(notificaciones, times(2)).recordatorio(turnoCon(turno.getId()));
    }

    @Test
    void laAlertaQueFallaSeReintentaYNoSeRepiteAlSalir() {
        Turno turno = guardar(en(12), EstadoTurno.PENDIENTE, true, false);
        when(notificaciones.turnoNoConfirmado(any())).thenReturn(false, true);

        servicio.ejecutarCiclo(AHORA);
        assertThat(releer(turno).isAlertaPendienteEnviada()).isFalse();
        servicio.ejecutarCiclo(AHORA);
        servicio.ejecutarCiclo(AHORA);

        assertThat(releer(turno).isAlertaPendienteEnviada()).isTrue();
        verify(notificaciones, times(2)).turnoNoConfirmado(turnoCon(turno.getId()));
    }

    @Test
    void unLoteDeTresConElSegundoFallandoMarcaElPrimeroYElTercero() {
        Turno t1 = pendiente(en(30));
        Turno t2 = pendiente(en(31));
        Turno t3 = pendiente(en(32));
        when(notificaciones.recordatorio(turnoCon(t1.getId()))).thenReturn(true);
        when(notificaciones.recordatorio(turnoCon(t2.getId())))
                .thenThrow(new IllegalStateException("fallo ficticio"));
        when(notificaciones.recordatorio(turnoCon(t3.getId()))).thenReturn(true);

        ResultadoCiclo resultado = servicio.ejecutarCiclo(AHORA);

        assertThat(releer(t1).isRecordatorioEnviado()).isTrue();
        assertThat(releer(t2).isRecordatorioEnviado()).isFalse();
        assertThat(releer(t3).isRecordatorioEnviado()).isTrue();
        assertThat(resultado.recordatoriosEnviados()).isEqualTo(2);
        assertThat(resultado.recordatoriosFallidos()).isEqualTo(1);
    }

    @Test
    void elTurnoCreadoEntre24Y48HConElRecordatorioFallidoSeReintentaEnElCiclo() {
        // Al crearlo el aviso falla (false); en el ciclo sale.
        when(notificaciones.recordatorio(any())).thenReturn(false, true);
        turnoService.crearManual(manual(en(30)));
        Turno creado = turnos.findAll().getFirst();
        assertThat(creado.getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
        assertThat(creado.isRecordatorioEnviado()).isFalse();

        servicio.ejecutarCiclo(AHORA);

        assertThat(releer(creado).isRecordatorioEnviado()).isTrue();
        verify(notificaciones, times(2)).recordatorio(any());
    }

    // --- 5.2 ---

    @Test
    void elTurnoCreadoAMenosDe24HNaceConfirmadoYNoRecibeAvisos() {
        when(notificaciones.avisoTurnoConfirmado(any())).thenReturn(true);
        turnoService.crearManual(manual(en(7)));
        Turno creado = turnos.findAll().getFirst();
        assertThat(creado.getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);

        servicio.ejecutarCiclo(AHORA);

        verify(notificaciones, never()).recordatorio(any());
        verify(notificaciones, never()).turnoNoConfirmado(any());
    }

    @Test
    void elTurnoPendienteConfirmadoPorElOdontologoNoRecibeLaAlerta() {
        Turno turno = guardar(en(10), EstadoTurno.PENDIENTE, true, false);
        turnoService.confirmar(turno.getId());

        servicio.ejecutarCiclo(AHORA);

        verify(notificaciones, never()).turnoNoConfirmado(any());
        assertThat(releer(turno).isAlertaPendienteEnviada()).isFalse();
    }

    // --- 5.3 ---

    @Test
    void reprogramarAMasDe48HReiniciaLosAvisosParaElNuevoHorario() {
        Turno turno = guardar(en(30), EstadoTurno.PENDIENTE, true, true);
        turnoService.reprogramar(turno.getId(), en(72));
        Turno reprogramado = releer(turno);
        assertThat(reprogramado.isRecordatorioEnviado()).isFalse();
        assertThat(reprogramado.isAlertaPendienteEnviada()).isFalse();
        when(notificaciones.recordatorio(any())).thenReturn(true);

        servicio.ejecutarCiclo(AHORA);
        verify(notificaciones, never()).recordatorio(any());

        Instant masTarde = AHORA.plus(Duration.ofHours(25));
        servicio.ejecutarCiclo(masTarde);
        servicio.ejecutarCiclo(masTarde);

        verify(notificaciones, times(1)).recordatorio(turnoCon(turno.getId()));
        verify(notificaciones, never()).turnoNoConfirmado(any());
        assertThat(releer(turno).isRecordatorioEnviado()).isTrue();
    }

    @Test
    void reprogramarAMenosDe24HDejaElTurnoConfirmadoYSinAvisos() {
        Turno turno = guardar(en(30), EstadoTurno.PENDIENTE, true, true);
        turnoService.reprogramar(turno.getId(), en(7));
        assertThat(releer(turno).getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);

        servicio.ejecutarCiclo(AHORA);

        verify(notificaciones, never()).recordatorio(any());
        verify(notificaciones, never()).turnoNoConfirmado(any());
    }

    // --- 5.4 ---

    @Test
    void siElTurnoSeReprogramaDuranteElEnvioElCicloConservaElNuevoInicioYElFlagEnFalse() {
        Turno turno = pendiente(en(30));
        Instant nuevoInicio = en(30).plus(Duration.ofDays(5));
        when(notificaciones.recordatorio(any())).thenAnswer(invocacion -> {
            actualizarDesdeOtraConexion(
                    "UPDATE turno SET inicio = ?, fin = ?, recordatorio_enviado = false WHERE id = ?",
                    Timestamp.from(nuevoInicio), Timestamp.from(nuevoInicio.plus(Duration.ofMinutes(30))),
                    turno.getId());
            return true;
        });

        servicio.ejecutarCiclo(AHORA);

        Turno releido = releer(turno);
        assertThat(releido.getInicio()).isEqualTo(nuevoInicio);
        assertThat(releido.isRecordatorioEnviado()).isFalse();
        assertThat(releido.getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
    }

    @Test
    void siElTurnoSeConfirmaDuranteElEnvioDeLaAlertaSigueConfirmado() {
        Turno turno = guardar(en(10), EstadoTurno.PENDIENTE, true, false);
        when(notificaciones.turnoNoConfirmado(any())).thenAnswer(invocacion -> {
            actualizarDesdeOtraConexion("UPDATE turno SET estado = 'CONFIRMADO' WHERE id = ?", turno.getId());
            return true;
        });

        servicio.ejecutarCiclo(AHORA);

        Turno releido = releer(turno);
        assertThat(releido.getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);
        assertThat(releido.isAlertaPendienteEnviada()).isFalse();
    }

    @Test
    void siElTurnoSeCancelaDuranteElEnvioDelRecordatorioSigueCancelado() {
        Turno turno = pendiente(en(30));
        when(notificaciones.recordatorio(any())).thenAnswer(invocacion -> {
            actualizarDesdeOtraConexion(
                    "UPDATE turno SET estado = 'CANCELADO', cancelado_por = 'PACIENTE' WHERE id = ?",
                    turno.getId());
            return true;
        });

        servicio.ejecutarCiclo(AHORA);

        Turno releido = releer(turno);
        assertThat(releido.getEstado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(releido.isRecordatorioEnviado()).isFalse();
    }

    // --- 4.2 ---

    @Test
    void enElPerfilDeTestElJobNoExisteYElServicioSi() {
        assertThat(contexto.getBeansOfType(RecordatorioJob.class)).isEmpty();
        assertThat(contexto.getBeansOfType(AvisosProgramadosService.class)).hasSize(1);
    }

    private TurnoManualRequest manual(Instant inicio) {
        return new TurnoManualRequest(prestacion.getId(), inicio.atOffset(ZoneOffset.ofHours(-3)),
                "paciente@ejemplo.com", "1155555555", paciente.getId(), null);
    }
}
