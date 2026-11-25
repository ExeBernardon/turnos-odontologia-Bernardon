package ar.turnos.odontologia.disponibilidad;

import static ar.turnos.odontologia.disponibilidad.ModoDisponibilidad.ODONTOLOGO;
import static ar.turnos.odontologia.disponibilidad.ModoDisponibilidad.PUBLICA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import ar.turnos.odontologia.bloqueo.Bloqueo;
import ar.turnos.odontologia.bloqueo.BloqueoRepository;
import ar.turnos.odontologia.common.ApiException;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.horario.HorarioAtencion;
import ar.turnos.odontologia.horario.HorarioAtencionRepository;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;
import ar.turnos.odontologia.turno.EstadoTurno;
import ar.turnos.odontologia.turno.Turno;
import ar.turnos.odontologia.turno.TurnoRepository;

/** Tests unitarios de la fachada: mocks de repositorios, reloj fijo y sin base de datos. */
@ExtendWith(MockitoExtension.class)
class DisponibilidadServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    /** 08:45 local del lunes 30/11/2026: 09:00 queda a 15 min y 11:00 queda dentro de la ventana pública. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-30T11:45:00Z"), ZoneOffset.UTC);
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 30);
    private static final String MSG_NO_ENCONTRADO = "No encontramos el recurso solicitado.";

    @Mock
    private PrestacionRepository prestaciones;
    @Mock
    private HorarioAtencionRepository horarios;
    @Mock
    private BloqueoRepository bloqueos;
    @Mock
    private TurnoRepository turnos;

    private DisponibilidadService service;
    private Prestacion consulta;

    @BeforeEach
    void setUp() {
        service = new DisponibilidadService(prestaciones, horarios, bloqueos, turnos, RELOJ, propiedades());
        consulta = new Prestacion("Consulta", 30);
        ReflectionTestUtils.setField(consulta, "id", 1L);
    }

    // ---- 1.1 / 2.4: API de validación reutilizable ----

    @Test
    void validarHorarioSobreBloqueoDa422ConLaCausa() {
        prestacionConHorario();
        when(bloqueos.findQueIntersecan(iniDia(), finDia()))
                .thenReturn(List.of(new Bloqueo(instant("11:00"), instant("12:00"), "Control")));
        when(turnos.findActivosQueIntersecan(iniDia(), finDia())).thenReturn(List.of());

        ApiException e = catchThrowableOfType(
                () -> service.validarHorario(consulta, instant("11:00"), ContextoValidacion.publica()),
                ApiException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(e.getCodigo()).isEqualTo("HORARIO_NO_DISPONIBLE");
        assertThat(e.getMessage()).contains("no está disponible");
    }

    @Test
    void validarHorarioConTurnoActivoDa422() {
        prestacionConHorario();
        when(bloqueos.findQueIntersecan(iniDia(), finDia())).thenReturn(List.of());
        when(turnos.findActivosQueIntersecan(iniDia(), finDia()))
                .thenReturn(List.of(turno(7L, instant("11:00"), 30)));

        ApiException e = catchThrowableOfType(
                () -> service.validarHorario(consulta, instant("11:00"), ContextoValidacion.publica()),
                ApiException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(e.getCodigo()).isEqualTo("HORARIO_NO_DISPONIBLE");
    }

    @Test
    void cargaManualOdontologoDentroDeLaProximaHoraPasa() {
        prestacionConHorario();
        when(bloqueos.findQueIntersecan(iniDia(), finDia())).thenReturn(List.of());
        when(turnos.findActivosQueIntersecan(iniDia(), finDia())).thenReturn(List.of());

        assertThatCode(() -> service.validarHorario(consulta, instant("09:00"), ContextoValidacion.odontologo()))
                .doesNotThrowAnyException();
    }

    @Test
    void reservaPublicaDentroDeLasDosHorasDa422PorVentana() {
        prestacionConHorario();
        when(bloqueos.findQueIntersecan(iniDia(), finDia())).thenReturn(List.of());
        when(turnos.findActivosQueIntersecan(iniDia(), finDia())).thenReturn(List.of());

        ApiException e = catchThrowableOfType(
                () -> service.validarHorario(consulta, instant("09:00"), ContextoValidacion.publica()),
                ApiException.class);

        assertThat(e.getCodigo()).isEqualTo("HORARIO_NO_DISPONIBLE");
        assertThat(e.getMessage()).contains("ventana");
    }

    @Test
    void reprogramacionExcluyeElTurnoPropio() {
        prestacionConHorario();
        when(bloqueos.findQueIntersecan(iniDia(), finDia())).thenReturn(List.of());
        when(turnos.findActivosQueIntersecan(iniDia(), finDia()))
                .thenReturn(List.of(turno(7L, instant("09:00"), 30)));

        assertThatCode(() -> service.validarHorario(consulta, instant("09:00"),
                new ContextoValidacion(ODONTOLOGO, 7L))).doesNotThrowAnyException();

        assertThatThrownBy(() -> service.validarHorario(consulta, instant("09:00"), ContextoValidacion.odontologo()))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("HORARIO_NO_DISPONIBLE");
                    assertThat(e.getMessage()).contains("no está disponible");
                });
    }

    @Test
    void prestacionInactivaDa422AntesDeConsultarRepositorios() {
        consulta.setActiva(false);

        assertThatThrownBy(() -> service.validarHorario(consulta, instant("11:00"), ContextoValidacion.publica()))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getCodigo()).isEqualTo("PRESTACION_INACTIVA");
                });
    }

    @Test
    void validarHorarioConPrestacionInexistenteDa404() {
        when(prestaciones.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validarHorario(99L, instant("11:00"), ContextoValidacion.publica()))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getCodigo()).isEqualTo("RECURSO_NO_ENCONTRADO");
                    assertThat(e.getMessage()).isEqualTo(MSG_NO_ENCONTRADO);
                });

        assertThatThrownBy(() -> service.validarHorario((Prestacion) null, instant("11:00"),
                ContextoValidacion.publica()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---- 3.2: consulta por rango ----

    @Test
    void consultarUnDiaDevuelveLaPrestacionYLosHorariosConOffset() {
        when(prestaciones.findById(1L)).thenReturn(Optional.of(consulta));
        when(horarios.findAll()).thenReturn(List.of(horario()));
        when(bloqueos.findQueIntersecan(iniDia(), finDia())).thenReturn(List.of());
        when(turnos.findActivosQueIntersecan(iniDia(), finDia())).thenReturn(List.of());

        DisponibilidadResponse r = service.consultar(1L, LUNES, LUNES, ODONTOLOGO);

        assertThat(r.prestacion()).isEqualTo(new PrestacionRefResponse(1L, "Consulta", 30));
        assertThat(r.dias()).hasSize(1);
        assertThat(r.dias().get(0).fecha()).isEqualTo(LUNES);
        assertThat(r.dias().get(0).horarios()).contains(instant("09:00").atOffset(ZoneOffset.ofHours(-3)));
        assertThat(r.dias().get(0).horarios()).allSatisfy(h -> assertThat(h.getOffset()).isEqualTo(ZoneOffset.ofHours(-3)));
    }

    @Test
    void consultarVariosDiasIncluyeDiaSinAtencionConHorariosVacios() {
        LocalDate hasta = LUNES.plusDays(2);
        when(prestaciones.findById(1L)).thenReturn(Optional.of(consulta));
        when(horarios.findAll()).thenReturn(List.of(horario()));
        when(bloqueos.findQueIntersecan(iniDia(LUNES, hasta), finDia(LUNES, hasta))).thenReturn(List.of());
        when(turnos.findActivosQueIntersecan(iniDia(LUNES, hasta), finDia(LUNES, hasta))).thenReturn(List.of());

        DisponibilidadResponse r = service.consultar(1L, LUNES, hasta, ODONTOLOGO);

        assertThat(r.dias()).extracting(DiaDisponibleResponse::fecha)
                .containsExactly(LUNES, LUNES.plusDays(1), hasta);
        assertThat(r.dias().get(0).horarios()).isNotEmpty();
        assertThat(r.dias().get(1).horarios()).isEmpty();
        assertThat(r.dias().get(2).horarios()).isEmpty();
    }

    @Test
    void consultarTurnoCanceladoNoQuitaHorarios() {
        when(prestaciones.findById(1L)).thenReturn(Optional.of(consulta));
        when(horarios.findAll()).thenReturn(List.of(horario()));
        when(bloqueos.findQueIntersecan(iniDia(), finDia())).thenReturn(List.of());
        when(turnos.findActivosQueIntersecan(iniDia(), finDia())).thenReturn(List.of());

        DisponibilidadResponse r = service.consultar(1L, LUNES, LUNES, ODONTOLOGO);

        assertThat(r.dias().get(0).horarios()).contains(instant("10:00").atOffset(ZoneOffset.ofHours(-3)));
        verify(turnos).findActivosQueIntersecan(iniDia(), finDia());
    }

    @Test
    void consultarConDosTurnosActivosRestambos() {
        when(prestaciones.findById(1L)).thenReturn(Optional.of(consulta));
        when(horarios.findAll()).thenReturn(List.of(horario()));
        when(bloqueos.findQueIntersecan(iniDia(), finDia())).thenReturn(List.of());
        when(turnos.findActivosQueIntersecan(iniDia(), finDia()))
                .thenReturn(List.of(turno(1L, instant("10:00"), 30), turno(2L, instant("11:00"), 30)));

        DisponibilidadResponse r = service.consultar(1L, LUNES, LUNES, ODONTOLOGO);

        assertThat(r.dias().get(0).horarios())
                .contains(instant("09:00").atOffset(ZoneOffset.ofHours(-3)))
                .doesNotContain(instant("10:00").atOffset(ZoneOffset.ofHours(-3)),
                        instant("11:00").atOffset(ZoneOffset.ofHours(-3)));
    }

    @Test
    void rangoInvertidoOMasDe31DiasDa400() {
        assertThatThrownBy(() -> service.consultar(1L, LUNES, LUNES.minusDays(1), ODONTOLOGO))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getCodigo()).isEqualTo("VALIDACION");
                });
        assertThatThrownBy(() -> service.consultar(1L, LUNES, LUNES.plusDays(31), ODONTOLOGO))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getCodigo()).isEqualTo("VALIDACION");
                });
        assertThatThrownBy(() -> service.consultar(1L, null, LUNES, ODONTOLOGO))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void consultarPrestacionInexistenteDa404EInactivaDa422() {
        when(prestaciones.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.consultar(99L, LUNES, LUNES, ODONTOLOGO))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getCodigo()).isEqualTo("RECURSO_NO_ENCONTRADO");
                });

        consulta.setActiva(false);
        when(prestaciones.findById(1L)).thenReturn(Optional.of(consulta));
        assertThatThrownBy(() -> service.consultar(1L, LUNES, LUNES, PUBLICA))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getCodigo()).isEqualTo("PRESTACION_INACTIVA");
                });
    }

    @Test
    void consultaPublicaAplicaLaVentanaYLaPrivadaNo() {
        when(prestaciones.findById(1L)).thenReturn(Optional.of(consulta));
        when(horarios.findAll()).thenReturn(List.of(horario()));
        when(bloqueos.findQueIntersecan(iniDia(), finDia())).thenReturn(List.of());
        when(turnos.findActivosQueIntersecan(iniDia(), finDia())).thenReturn(List.of());

        DisponibilidadResponse publica = service.consultar(1L, LUNES, LUNES, PUBLICA);
        DisponibilidadResponse privada = service.consultar(1L, LUNES, LUNES, ODONTOLOGO);

        assertThat(publica.dias().get(0).horarios()).doesNotContain(instant("09:00").atOffset(ZoneOffset.ofHours(-3)));
        assertThat(privada.dias().get(0).horarios()).contains(instant("09:00").atOffset(ZoneOffset.ofHours(-3)));
    }

    // ---- helpers ----

    private void prestacionConHorario() {
        when(horarios.findByDiaSemanaOrderByHoraInicio(LUNES.getDayOfWeek())).thenReturn(List.of(horario()));
    }

    private static HorarioAtencion horario() {
        return new HorarioAtencion(LUNES.getDayOfWeek(), LocalTime.parse("09:00"), LocalTime.parse("13:00"));
    }

    private static Turno turno(long id, Instant inicio, int minutos) {
        Turno t = new Turno(new Paciente("Ana", "Prueba", "99999999"), new Prestacion("Consulta", 30), inicio,
                inicio.plusSeconds(minutos * 60L), EstadoTurno.PENDIENTE, "tok-ficticio-" + id,
                "paciente@ejemplo.com", "11 5555-5555");
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private static Instant instant(String horaLocal) {
        return LocalDate.of(2026, 11, 30).atTime(LocalTime.parse(horaLocal)).atZone(ZONA).toInstant();
    }

    private static Instant iniDia() {
        return iniDia(LUNES, LUNES);
    }

    private static Instant finDia() {
        return finDia(LUNES, LUNES);
    }

    private static Instant iniDia(LocalDate desde, LocalDate hasta) {
        return desde.atStartOfDay(ZONA).toInstant();
    }

    private static Instant finDia(LocalDate desde, LocalDate hasta) {
        return hasta.plusDays(1).atStartOfDay(ZONA).toInstant();
    }

    private static AppProperties propiedades() {
        return new AppProperties(ZONA, "http://localhost", new AppProperties.Agenda(15),
                new AppProperties.Recordatorio(15, 24), new AppProperties.Alerta(2),
                new AppProperties.Cancelacion(24), new AppProperties.Turno(2, 60),
                new AppProperties.Consultorio("consultorio@ejemplo.com"),
                new AppProperties.Mail("noreply@ejemplo.com"),
                new AppProperties.Jwt("secreto-ficticio-de-prueba", 60),
                new AppProperties.Seed(new AppProperties.Seed.Odontologo("Odontólogo Ejemplo",
                        "odontologo@ejemplo.com", "clave-ficticia")));
    }
}
