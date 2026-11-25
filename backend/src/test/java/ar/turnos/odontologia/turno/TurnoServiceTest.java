package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import ar.turnos.odontologia.AppPropertiesFixture;
import ar.turnos.odontologia.common.ApiException;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.disponibilidad.ContextoValidacion;
import ar.turnos.odontologia.disponibilidad.DisponibilidadService;
import ar.turnos.odontologia.notificacion.NotificacionTurnos;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.paciente.PacienteRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;

/** Reglas de negocio de la alta, con reloj fijo y sin base de datos. */
@ExtendWith(MockitoExtension.class)
class TurnoServiceTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-12-01T12:00:00Z"), ZoneOffset.UTC);
    private static final Instant INICIO = RELOJ.instant().plus(Duration.ofHours(72));
    private static final AppProperties APP = AppPropertiesFixture.crear();
    private static final String TOKEN = "tokenFicticioDePruebaConLonitudValidaParaElContrato123";

    @Mock
    private TurnoRepository turnos;
    @Mock
    private PacienteRepository pacientes;
    @Mock
    private PrestacionRepository prestaciones;
    @Mock
    private DisponibilidadService disponibilidad;
    @Mock
    private NotificacionTurnos notificaciones;
    @Mock
    private GeneradorToken generador;

    private TurnoService service;

    @BeforeEach
    void setUp() {
        TurnoMapper mapper = new TurnoMapper(RELOJ, APP.zonaHoraria(), APP.consultorio().contacto());
        service = new TurnoService(turnos, pacientes, prestaciones, disponibilidad, notificaciones,
                generador, mapper, RELOJ);
    }

    @Test
    void sinConsentimientoEs422YNadaSePersiste() {
        ReservaPublicaRequest request = reserva("Otro Nombre", false);

        assertThatThrownBy(() -> service.reservar(request))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getCodigo()).isEqualTo("CONSENTIMIENTO_REQUERIDO");
                });

        verifyNoInteractions(turnos, disponibilidad, notificaciones, pacientes, prestaciones);
    }

    @Test
    void reservaValidaPersisteFinTokenContactoYReutilizaPacienteSinModificarlo() {
        Prestacion prestacion = new Prestacion("Consulta", 30);
        Paciente existente = new Paciente("Ana", "Ejemplo", "99999999");
        when(prestaciones.findById(1L)).thenReturn(Optional.of(prestacion));
        when(pacientes.findByDni("99999999")).thenReturn(Optional.of(existente));
        when(generador.generar()).thenReturn(TOKEN);
        when(turnos.saveAndFlush(any(Turno.class))).thenAnswer(inv -> inv.getArgument(0));

        TurnoPublicoResponse respuesta = service.reservar(reserva("Otro Nombre", true));

        ArgumentCaptor<Turno> captor = ArgumentCaptor.forClass(Turno.class);
        verify(turnos).saveAndFlush(captor.capture());
        Turno guardado = captor.getValue();
        assertThat(guardado.getPaciente()).isSameAs(existente);
        assertThat(existente.getNombre()).isEqualTo("Ana");
        assertThat(guardado.getPrestacion()).isSameAs(prestacion);
        assertThat(guardado.getInicio()).isEqualTo(INICIO);
        assertThat(guardado.getFin()).isEqualTo(INICIO.plus(Duration.ofMinutes(30)));
        assertThat(guardado.getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
        assertThat(guardado.getToken()).isEqualTo(TOKEN);
        assertThat(guardado.getEmailContacto()).isEqualTo("ana@ejemplo.com");
        assertThat(guardado.getTelefonoContacto()).isEqualTo("+54 9 11 0000-0000");
        assertThat(guardado.getConsentimientoAt()).isEqualTo(RELOJ.instant());
        assertThat(guardado.isRecordatorioEnviado()).isFalse();
        assertThat(guardado.isAlertaPendienteEnviada()).isFalse();

        assertThat(respuesta.estado()).isEqualTo(EstadoTurno.PENDIENTE);
        assertThat(respuesta.nombrePaciente()).isEqualTo("Ana");
        assertThat(respuesta.prestacion().duracionMinutos()).isEqualTo(30);
        assertThat(respuesta.contactoConsultorio()).isEqualTo(AppPropertiesFixture.CONTACTO);
        verify(disponibilidad).validarHorario(prestacion, INICIO, ContextoValidacion.publica());
    }

    @Test
    void prestacionInexistenteEs404() {
        when(prestaciones.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reservar(reserva("Ana", true)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getCodigo()).isEqualTo("RECURSO_NO_ENCONTRADO");
                });

        verifyNoInteractions(turnos, notificaciones);
    }

    @Test
    void prestacionInactivaEs422() {
        when(prestaciones.findById(1L)).thenReturn(Optional.of(new Prestacion("Consulta", 30)));
        doThrow(new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRESTACION_INACTIVA", "inactiva"))
                .when(disponibilidad).validarHorario(any(Prestacion.class), any(Instant.class),
                        any(ContextoValidacion.class));

        assertThatThrownBy(() -> service.reservar(reserva("Ana", true)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getCodigo()).isEqualTo("PRESTACION_INACTIVA");
                });

        verifyNoInteractions(turnos, notificaciones);
    }

    @Test
    void cargaManualConPacienteExistenteUsaContextoOdontologoYSinConsentimiento() {
        Prestacion prestacion = new Prestacion("Consulta", 30);
        Paciente existente = new Paciente("Ana", "Ejemplo", "99999999");
        when(prestaciones.findById(1L)).thenReturn(Optional.of(prestacion));
        when(pacientes.findById(7L)).thenReturn(Optional.of(existente));
        when(generador.generar()).thenReturn(TOKEN);
        when(turnos.saveAndFlush(any(Turno.class))).thenAnswer(inv -> inv.getArgument(0));

        TurnoManualRequest request = new TurnoManualRequest(1L, INICIO.atOffset(ZoneOffset.ofHours(-3)),
                "ana@ejemplo.com", "+54 9 11 0000-0000", 7L, null);
        TurnoResponse respuesta = service.crearManual(request);

        ArgumentCaptor<Turno> captor = ArgumentCaptor.forClass(Turno.class);
        verify(turnos).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getConsentimientoAt()).isNull();
        assertThat(captor.getValue().getPaciente()).isSameAs(existente);
        assertThat(respuesta.paciente().dni()).isEqualTo("99999999");
        assertThat(respuesta.consentimientoAt()).isNull();
        verify(disponibilidad).validarHorario(prestacion, INICIO, ContextoValidacion.odontologo());
    }

    @Test
    void altaRapidaConDniYaRegistradoEs422() {
        when(prestaciones.findById(1L)).thenReturn(Optional.of(new Prestacion("Consulta", 30)));
        when(pacientes.findByDni("99999999"))
                .thenReturn(Optional.of(new Paciente("Ana", "Ejemplo", "99999999")));

        TurnoManualRequest request = new TurnoManualRequest(1L, INICIO.atOffset(ZoneOffset.ofHours(-3)),
                "ana@ejemplo.com", "+54 9 11 0000-0000", null,
                new ar.turnos.odontologia.paciente.PacienteRequest("Otro", "Ejemplo", "99.999.999"));

        assertThatThrownBy(() -> service.crearManual(request))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getCodigo()).isEqualTo("DNI_DUPLICADO");
                });

        verifyNoInteractions(turnos, notificaciones);
    }

    @Test
    void altaRapidaCreaPacienteConDniNormalizado() {
        when(prestaciones.findById(1L)).thenReturn(Optional.of(new Prestacion("Consulta", 30)));
        when(pacientes.findByDni("99999999")).thenReturn(Optional.empty());
        when(pacientes.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));
        when(generador.generar()).thenReturn(TOKEN);
        when(turnos.saveAndFlush(any(Turno.class))).thenAnswer(inv -> inv.getArgument(0));

        TurnoManualRequest request = new TurnoManualRequest(1L, INICIO.atOffset(ZoneOffset.ofHours(-3)),
                "ana@ejemplo.com", "+54 9 11 0000-0000", null,
                new ar.turnos.odontologia.paciente.PacienteRequest("Ana", "Ejemplo", "99.999.999"));
        service.crearManual(request);

        ArgumentCaptor<Paciente> captor = ArgumentCaptor.forClass(Paciente.class);
        verify(pacientes).save(captor.capture());
        assertThat(captor.getValue().getDni()).isEqualTo("99999999");
    }

    private ReservaPublicaRequest reserva(String nombre, boolean consentimiento) {
        return new ReservaPublicaRequest(nombre, "Ejemplo", "99.999.999", "ana@ejemplo.com",
                "+54 9 11 0000-0000", 1L, INICIO.atOffset(ZoneOffset.ofHours(-3)), consentimiento);
    }
}
