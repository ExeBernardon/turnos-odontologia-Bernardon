package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import ar.turnos.odontologia.AppPropertiesFixture;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.turno.EstadoTurno;
import ar.turnos.odontologia.turno.Turno;

@ExtendWith(OutputCaptureExtension.class)
class AvisosProgramadosServiceTest {

    private static final Instant AHORA = Instant.parse("2026-12-07T12:00:00Z");
    private static final Duration H24 = Duration.ofHours(24);
    private static final Duration H48 = Duration.ofHours(48);

    private TurnoAvisosRepository avisos;
    private NotificacionTurnos notificaciones;
    private PlatformTransactionManager transacciones;
    private RelojContador reloj;
    private AvisosProgramadosService servicio;

    @BeforeEach
    void preparar() {
        avisos = mock(TurnoAvisosRepository.class);
        notificaciones = mock(NotificacionTurnos.class);
        transacciones = mock(PlatformTransactionManager.class);
        reloj = new RelojContador(AHORA);
        servicio = crearServicio(AppPropertiesFixture.crear());
    }

    private AvisosProgramadosService crearServicio(AppProperties app) {
        return new AvisosProgramadosService(avisos, notificaciones, app, reloj, transacciones);
    }

    private static AppProperties conHorasAntes(int horasAntes) {
        AppProperties base = AppPropertiesFixture.crear();
        return new AppProperties(base.zonaHoraria(), base.frontendBaseUrl(), base.agenda(),
                new AppProperties.Recordatorio(base.recordatorio().intervaloMin(), horasAntes), base.alerta(),
                base.cancelacion(), base.turno(), base.consultorio(), base.mail(), base.jwt(), base.seed());
    }

    private Turno turno(long id, Instant inicio, EstadoTurno estado) {
        Turno turno = new Turno(null, null, inicio, inicio.plus(Duration.ofMinutes(30)), estado,
                "token-ficticio-" + id, "paciente@ejemplo.com", "1155555555");
        ReflectionTestUtils.setField(turno, "id", id);
        when(avisos.findById(id)).thenReturn(Optional.of(turno));
        return turno;
    }

    private Turno pendiente(long id, Instant inicio) {
        return turno(id, inicio, EstadoTurno.PENDIENTE);
    }

    private void idsRecordatorio(Long... ids) {
        when(avisos.idsParaRecordatorio(any(), any())).thenReturn(List.of(ids));
    }

    private void idsAlerta(Long... ids) {
        when(avisos.idsParaAlerta(any(), any())).thenReturn(List.of(ids));
    }

    // --- 3.1 tarea de recordatorios ---

    @Test
    void recordatorioEnviadoConTrueMarcaElFlagConElInicioLeido() {
        Instant inicio = AHORA.plus(Duration.ofHours(30));
        Turno turno = pendiente(1L, inicio);
        idsRecordatorio(1L);
        when(notificaciones.recordatorio(turno)).thenReturn(true);

        servicio.ejecutarCiclo(AHORA);

        verify(notificaciones).recordatorio(turno);
        verify(avisos).marcarRecordatorioEnviado(1L, inicio);
    }

    @Test
    void recordatorioConFalseNoMarcaElFlag() {
        Turno turno = pendiente(1L, AHORA.plus(Duration.ofHours(30)));
        idsRecordatorio(1L);
        when(notificaciones.recordatorio(turno)).thenReturn(false);

        servicio.ejecutarCiclo(AHORA);

        verify(notificaciones).recordatorio(turno);
        verify(avisos, never()).marcarRecordatorioEnviado(any(), any());
    }

    @Test
    void recordatorioDeUnTurnoQueAlReleerYaEstaConfirmadoNoSeEnvia() {
        turno(1L, AHORA.plus(Duration.ofHours(30)), EstadoTurno.CONFIRMADO);
        idsRecordatorio(1L);

        servicio.ejecutarCiclo(AHORA);

        verifyNoInteractions(notificaciones);
        verify(avisos, never()).marcarRecordatorioEnviado(any(), any());
    }

    @Test
    void recordatorioDeUnTurnoQueAlReleerQuedoFueraDeLaVentanaNoSeEnvia() {
        idsRecordatorio(1L, 2L);
        pendiente(1L, AHORA.plus(H48).plusSeconds(60));
        pendiente(2L, AHORA.minusSeconds(60));

        servicio.ejecutarCiclo(AHORA);

        verifyNoInteractions(notificaciones);
    }

    @Test
    void recordatorioDeUnTurnoQueDesaparecioAlReleerNoSeEnvia() {
        idsRecordatorio(7L);
        when(avisos.findById(7L)).thenReturn(Optional.empty());

        ResultadoCiclo resultado = servicio.ejecutarCiclo(AHORA);

        verifyNoInteractions(notificaciones);
        assertThat(resultado.recordatoriosEnviados()).isZero();
        assertThat(resultado.recordatoriosFallidos()).isZero();
    }

    // --- 3.2 tarea de alertas ---

    @Test
    void alertaEnviadaConTrueMarcaElFlagYNuncaLlamaARecordatorio() {
        Instant inicio = AHORA.plus(Duration.ofHours(10));
        Turno turno = pendiente(2L, inicio);
        idsAlerta(2L);
        when(notificaciones.turnoNoConfirmado(turno)).thenReturn(true);

        servicio.ejecutarCiclo(AHORA);

        verify(notificaciones).turnoNoConfirmado(turno);
        verify(avisos).marcarAlertaEnviada(2L, inicio);
        verify(notificaciones, never()).recordatorio(any());
        verify(avisos, never()).marcarRecordatorioEnviado(any(), any());
    }

    @Test
    void alertaConFalseNoMarcaElFlag() {
        Turno turno = pendiente(2L, AHORA.plus(Duration.ofHours(10)));
        idsAlerta(2L);
        when(notificaciones.turnoNoConfirmado(turno)).thenReturn(false);

        servicio.ejecutarCiclo(AHORA);

        verify(avisos, never()).marcarAlertaEnviada(any(), any());
    }

    @Test
    void alertaDeUnTurnoQueAlReleerEstaEnElBordeDe24HONoEstaPendienteNoSeEnvia() {
        idsAlerta(1L, 2L);
        pendiente(1L, AHORA.plus(H24));
        turno(2L, AHORA.plus(Duration.ofHours(10)), EstadoTurno.CONFIRMADO);

        servicio.ejecutarCiclo(AHORA);

        verifyNoInteractions(notificaciones);
    }

    // --- 3.3 ciclo completo ---

    @Test
    void elCicloTomaElInstanteUnaSolaVezYLoUsaEnAmbasTareasConLosUmbralesDeAppProperties() {
        servicio.ejecutarCiclo();

        assertThat(reloj.consultas()).hasValue(1);
        verify(avisos).idsParaRecordatorio(AHORA, AHORA.plus(H48));
        verify(avisos).idsParaAlerta(AHORA, AHORA.plus(H24));
    }

    @Test
    void laVentanaDelRecordatorioSigueAHorasAntesConfigurado() {
        crearServicio(conHorasAntes(72)).ejecutarCiclo();

        verify(avisos).idsParaRecordatorio(AHORA, AHORA.plus(Duration.ofHours(72)));
    }

    @Test
    void conVentanaDe72HUnTurnoA60HSeEnvia() {
        Instant inicio = AHORA.plus(Duration.ofHours(60));
        Turno turno = pendiente(1L, inicio);
        idsRecordatorio(1L);
        when(notificaciones.recordatorio(turno)).thenReturn(true);

        crearServicio(conHorasAntes(72)).ejecutarCiclo();

        verify(avisos).marcarRecordatorioEnviado(1L, inicio);
    }

    @Test
    void losRecordatoriosCorrenAntesQueLasAlertas() {
        Turno paraRecordatorio = pendiente(1L, AHORA.plus(Duration.ofHours(30)));
        Turno paraAlerta = pendiente(2L, AHORA.plus(Duration.ofHours(10)));
        idsRecordatorio(1L);
        idsAlerta(2L);
        when(notificaciones.recordatorio(paraRecordatorio)).thenReturn(true);
        when(notificaciones.turnoNoConfirmado(paraAlerta)).thenReturn(true);

        servicio.ejecutarCiclo(AHORA);

        InOrder orden = inOrder(notificaciones);
        orden.verify(notificaciones).recordatorio(paraRecordatorio);
        orden.verify(notificaciones).turnoNoConfirmado(paraAlerta);
    }

    @Test
    void sinTurnosElegiblesNoSeLlamaANotificacionTurnos() {
        ResultadoCiclo resultado = servicio.ejecutarCiclo(AHORA);

        verifyNoInteractions(notificaciones);
        assertThat(resultado).isEqualTo(new ResultadoCiclo(0, 0, 0, 0));
    }

    @Test
    void elResultadoCuentaEnviadosYFallidosPorTarea() {
        Turno r1 = pendiente(1L, AHORA.plus(Duration.ofHours(30)));
        Turno r2 = pendiente(2L, AHORA.plus(Duration.ofHours(31)));
        Turno r3 = pendiente(3L, AHORA.plus(Duration.ofHours(32)));
        Turno a1 = pendiente(4L, AHORA.plus(Duration.ofHours(10)));
        Turno a2 = pendiente(5L, AHORA.plus(Duration.ofHours(11)));
        idsRecordatorio(1L, 2L, 3L);
        idsAlerta(4L, 5L);
        when(notificaciones.recordatorio(r1)).thenReturn(true);
        when(notificaciones.recordatorio(r2)).thenReturn(false);
        when(notificaciones.recordatorio(r3)).thenReturn(true);
        when(notificaciones.turnoNoConfirmado(a1)).thenReturn(false);
        when(notificaciones.turnoNoConfirmado(a2)).thenReturn(false);

        ResultadoCiclo resultado = servicio.ejecutarCiclo(AHORA);

        assertThat(resultado).isEqualTo(new ResultadoCiclo(2, 1, 0, 2));
    }

    // --- 3.4 aislamiento de fallos ---

    @Test
    void unaExcepcionEnElSegundoRecordatorioNoImpideMarcarElPrimeroYElTercero() {
        Turno t1 = pendiente(1L, AHORA.plus(Duration.ofHours(30)));
        Turno t2 = pendiente(2L, AHORA.plus(Duration.ofHours(31)));
        Turno t3 = pendiente(3L, AHORA.plus(Duration.ofHours(32)));
        idsRecordatorio(1L, 2L, 3L);
        when(notificaciones.recordatorio(t1)).thenReturn(true);
        when(notificaciones.recordatorio(t2)).thenThrow(new IllegalStateException("fallo inesperado"));
        when(notificaciones.recordatorio(t3)).thenReturn(true);

        ResultadoCiclo resultado = servicio.ejecutarCiclo(AHORA);

        verify(avisos).marcarRecordatorioEnviado(1L, t1.getInicio());
        verify(avisos, never()).marcarRecordatorioEnviado(2L, t2.getInicio());
        verify(avisos).marcarRecordatorioEnviado(3L, t3.getInicio());
        assertThat(resultado.recordatoriosEnviados()).isEqualTo(2);
        assertThat(resultado.recordatoriosFallidos()).isEqualTo(1);
    }

    @Test
    void siFallanTodosLosRecordatoriosLaTareaDeAlertasIgualCorre() {
        Turno r1 = pendiente(1L, AHORA.plus(Duration.ofHours(30)));
        Turno r2 = pendiente(2L, AHORA.plus(Duration.ofHours(31)));
        Turno alerta = pendiente(3L, AHORA.plus(Duration.ofHours(10)));
        idsRecordatorio(1L, 2L);
        idsAlerta(3L);
        when(notificaciones.recordatorio(r1)).thenThrow(new RuntimeException("uno"));
        when(notificaciones.recordatorio(r2)).thenThrow(new RuntimeException("dos"));
        when(notificaciones.turnoNoConfirmado(alerta)).thenReturn(true);

        ResultadoCiclo resultado = servicio.ejecutarCiclo(AHORA);

        verify(avisos).marcarAlertaEnviada(3L, alerta.getInicio());
        assertThat(resultado).isEqualTo(new ResultadoCiclo(0, 2, 1, 0));
    }

    @Test
    void unaExcepcionEnLaAlertaNoImpideMarcarLasDemas() {
        Turno a1 = pendiente(1L, AHORA.plus(Duration.ofHours(10)));
        Turno a2 = pendiente(2L, AHORA.plus(Duration.ofHours(11)));
        idsAlerta(1L, 2L);
        when(notificaciones.turnoNoConfirmado(a1)).thenThrow(new RuntimeException("falla"));
        when(notificaciones.turnoNoConfirmado(a2)).thenReturn(true);

        servicio.ejecutarCiclo(AHORA);

        verify(avisos).marcarAlertaEnviada(2L, a2.getInicio());
        verify(avisos, never()).marcarAlertaEnviada(1L, a1.getInicio());
    }

    @Test
    void elLogDelFalloTieneElIdYLaTareaPeroNingunDatoPersonalNiElMensajeDeLaExcepcion(CapturedOutput salida) {
        Turno turno = pendiente(42L, AHORA.plus(Duration.ofHours(30)));
        idsRecordatorio(42L);
        when(notificaciones.recordatorio(turno)).thenThrow(new IllegalStateException(
                "rechazado: paciente@ejemplo.com 99999999 1155555555 token-ficticio-42"));

        servicio.ejecutarCiclo(AHORA);

        assertThat(salida.getAll())
                .contains("turnoId=42")
                .contains("RECORDATORIO")
                .contains("IllegalStateException")
                .doesNotContain("paciente@ejemplo.com")
                .doesNotContain("99999999")
                .doesNotContain("1155555555")
                .doesNotContain("token-ficticio-42")
                .doesNotContain("rechazado");
    }

    @Test
    void elLogDelFalloDeLaAlertaIndicaLaTareaTurnoNoConfirmado(CapturedOutput salida) {
        Turno turno = pendiente(43L, AHORA.plus(Duration.ofHours(10)));
        idsAlerta(43L);
        when(notificaciones.turnoNoConfirmado(turno)).thenThrow(new RuntimeException("odontologo@ejemplo.com"));

        servicio.ejecutarCiclo(AHORA);

        assertThat(salida.getAll())
                .contains("turnoId=43")
                .contains("TURNO_NO_CONFIRMADO")
                .doesNotContain("odontologo@ejemplo.com");
    }

    @Test
    void elCicloSinEnviosNiFallosNoEscribeElResumen(CapturedOutput salida) {
        servicio.ejecutarCiclo(AHORA);

        assertThat(salida.getAll()).doesNotContain("Ciclo de avisos");
    }

    @Test
    void elCicloConEnviosLogueaUnResumenConContadores(CapturedOutput salida) {
        Turno turno = pendiente(1L, AHORA.plus(Duration.ofHours(30)));
        idsRecordatorio(1L);
        when(notificaciones.recordatorio(turno)).thenReturn(true);

        servicio.ejecutarCiclo(AHORA);

        assertThat(salida.getAll()).contains("Ciclo de avisos").contains("recordatoriosEnviados=1");
    }

    /** Reloj fijo que cuenta cuantas veces se pidio el instante. */
    private static final class RelojContador extends Clock {
        private final Instant instante;
        private final AtomicInteger consultas = new AtomicInteger();

        RelojContador(Instant instante) {
            this.instante = instante;
        }

        AtomicInteger consultas() {
            return consultas;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            consultas.incrementAndGet();
            return instante;
        }
    }
}
