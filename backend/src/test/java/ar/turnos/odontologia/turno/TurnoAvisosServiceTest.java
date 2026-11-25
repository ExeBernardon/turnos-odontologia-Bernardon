package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ar.turnos.odontologia.AppPropertiesFixture;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.disponibilidad.DisponibilidadService;
import ar.turnos.odontologia.notificacion.NotificacionTurnos;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.paciente.PacienteRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;

/** Avisos del momento de alta segun anticipacion, con la franja [24 h, 48 h] inclusiva. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurnoAvisosServiceTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-12-01T12:00:00Z"), ZoneOffset.UTC);
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
        service = new TurnoService(turnos, pacientes, prestaciones, disponibilidad, notificaciones,
                generador, new TurnoMapper(RELOJ, APP.zonaHoraria(), APP.consultorio().contacto()), RELOJ, APP);

        when(prestaciones.findById(1L)).thenReturn(Optional.of(new Prestacion("Consulta", 30)));
        when(pacientes.findByDni("99999999"))
                .thenReturn(Optional.of(new Paciente("Ana", "Ejemplo", "99999999")));
        when(generador.generar()).thenReturn(TOKEN);
        when(turnos.saveAndFlush(any(Turno.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificaciones.confirmacionReserva(any(Turno.class))).thenReturn(true);
        when(notificaciones.recordatorio(any(Turno.class))).thenReturn(true);
        when(notificaciones.avisoTurnoConfirmado(any(Turno.class))).thenReturn(true);
    }

    @Test
    void masDe48HorasEnviaSoloConfirmacionDeReserva() {
        Turno turno = reservar(Duration.ofHours(72));

        verify(notificaciones).confirmacionReserva(turno);
        verify(notificaciones, never()).recordatorio(any(Turno.class));
        verify(notificaciones, never()).avisoTurnoConfirmado(any(Turno.class));
        assertThat(turno.getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
        assertThat(turno.isRecordatorioEnviado()).isFalse();
        assertThat(turno.isAlertaPendienteEnviada()).isFalse();
        assertThat(turno.getEmailContacto()).isEqualTo("ana@ejemplo.com");
    }

    @Test
    void exactamente48HorasEnviaRecordatorio() {
        Turno turno = reservar(Duration.ofHours(48));

        verify(notificaciones).recordatorio(turno);
        verify(notificaciones, never()).confirmacionReserva(any(Turno.class));
        assertThat(turno.isRecordatorioEnviado()).isTrue();
    }

    @Test
    void entre24y48HorasEnviaRecordatorioEnElMomento() {
        Turno turno = reservar(Duration.ofHours(36));

        verify(notificaciones).recordatorio(turno);
        assertThat(turno.getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
        assertThat(turno.isRecordatorioEnviado()).isTrue();
    }

    @Test
    void exactamente24HorasEnviaRecordatorio() {
        Turno turno = reservar(Duration.ofHours(24));

        verify(notificaciones).recordatorio(turno);
        assertThat(turno.isRecordatorioEnviado()).isTrue();
    }

    @Test
    void menosDe24HorasEnviaAvisoDeConfirmadoSinBotones() {
        Turno turno = reservar(Duration.ofHours(24).minusNanos(1));

        verify(notificaciones).avisoTurnoConfirmado(turno);
        verify(notificaciones, never()).recordatorio(any(Turno.class));
        assertThat(turno.getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);
        assertThat(turno.isRecordatorioEnviado()).isTrue();
        assertThat(turno.isAlertaPendienteEnviada()).isTrue();
    }

    @Test
    void unFalloDeAvisoNoRevierteLaReserva() {
        when(notificaciones.confirmacionReserva(any(Turno.class))).thenReturn(false);
        when(notificaciones.recordatorio(any(Turno.class))).thenReturn(false);
        when(notificaciones.avisoTurnoConfirmado(any(Turno.class))).thenReturn(false);

        assertThatCode(() -> reservar(Duration.ofHours(36))).doesNotThrowAnyException();

        ArgumentCaptor<Turno> captor = ArgumentCaptor.forClass(Turno.class);
        verify(turnos).saveAndFlush(captor.capture());
        assertThat(captor.getValue().isRecordatorioEnviado()).isFalse();
    }

    private Turno reservar(Duration anticipacion) {
        Instant inicio = RELOJ.instant().plus(anticipacion);
        service.reservar(new ReservaPublicaRequest("Ana", "Ejemplo", "99999999", "ana@ejemplo.com",
                "+54 9 11 0000-0000", 1L, inicio.atOffset(ZoneOffset.UTC), true));

        ArgumentCaptor<Turno> captor = ArgumentCaptor.forClass(Turno.class);
        verify(turnos).saveAndFlush(captor.capture());
        return captor.getValue();
    }
}
