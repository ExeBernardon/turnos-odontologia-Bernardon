package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ar.turnos.odontologia.common.ApiException;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.prestacion.Prestacion;

/** Máquina de estados: valida sin mutar; el service aplica el cambio después de los plazos. */
class TransicionesTurnoTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-12-01T12:00:00Z"), ZoneOffset.UTC);
    private static final Instant INICIO = RELOJ.instant().plus(Duration.ofHours(36));

    @Test
    void confirmarPendienteEsAplicableYConfirmadoEsIdempotente() {
        Turno pendiente = turno(EstadoTurno.PENDIENTE, INICIO);

        assertThat(TransicionesTurno.confirmar(pendiente, RELOJ)).isTrue();
        assertThat(pendiente.getEstado()).isEqualTo(EstadoTurno.PENDIENTE);

        Turno confirmado = turno(EstadoTurno.CONFIRMADO, INICIO);
        assertThat(TransicionesTurno.confirmar(confirmado, RELOJ)).isFalse();
    }

    @Test
    void confirmarCanceladoTerminalOIniciadoEsTransicionInvalida() {
        assertThatThrownBy(() -> TransicionesTurno.confirmar(
                turno(EstadoTurno.CANCELADO, INICIO), RELOJ))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getCodigo()).isEqualTo("TRANSICION_INVALIDA");
                    assertThat(e.getMessage()).isEqualTo("El turno está cancelado y no se puede confirmar.");
                });

        assertThatThrownBy(() -> TransicionesTurno.confirmar(
                turno(EstadoTurno.ASISTIO, INICIO), RELOJ))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("TRANSICION_INVALIDA");
                    assertThat(e.getMessage()).isEqualTo("Ese cambio de estado no está permitido para este turno.");
                });

        assertThatThrownBy(() -> TransicionesTurno.confirmar(
                turno(EstadoTurno.PENDIENTE, RELOJ.instant().minusSeconds(1)), RELOJ))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("TRANSICION_INVALIDA");
                });
    }

    @Test
    void cancelarPacienteSoloAplicaSobrePendiente() {
        Turno pendiente = turno(EstadoTurno.PENDIENTE, INICIO);
        assertThat(TransicionesTurno.cancelar(pendiente, CanceladoPor.PACIENTE)).isTrue();

        Turno cancelado = turno(EstadoTurno.CANCELADO, INICIO);
        assertThat(TransicionesTurno.cancelar(cancelado, CanceladoPor.PACIENTE)).isFalse();
    }

    @Test
    void cancelarPacienteConfirmadoOTerminalEsTransicionInvalidaConMensajePropio() {
        assertThatThrownBy(() -> TransicionesTurno.cancelar(
                turno(EstadoTurno.CONFIRMADO, INICIO), CanceladoPor.PACIENTE))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("TRANSICION_INVALIDA");
                    assertThat(e.getMessage()).isEqualTo(
                            "Tu turno ya está confirmado. Para cancelarlo comunicate con el consultorio.");
                });

        assertThatThrownBy(() -> TransicionesTurno.cancelar(
                turno(EstadoTurno.AUSENTE, INICIO), CanceladoPor.PACIENTE))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("TRANSICION_INVALIDA");
                    assertThat(e.getMessage()).isEqualTo("Ese cambio de estado no está permitido para este turno.");
                });
    }

    @Test
    void cancelarOdontologoAplicaSobreActivosYNuncaEsIdempotente() {
        assertThat(TransicionesTurno.cancelar(
                turno(EstadoTurno.PENDIENTE, INICIO), CanceladoPor.ODONTOLOGO)).isTrue();
        assertThat(TransicionesTurno.cancelar(
                turno(EstadoTurno.CONFIRMADO, RELOJ.instant().minusSeconds(1)),
                CanceladoPor.ODONTOLOGO)).isTrue();

        assertThatThrownBy(() -> TransicionesTurno.cancelar(
                turno(EstadoTurno.CANCELADO, INICIO), CanceladoPor.ODONTOLOGO))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("TRANSICION_INVALIDA");
                    assertThat(e.getMessage()).isEqualTo(
                            "El turno ya está cancelado o finalizado y no se puede cancelar.");
                });
    }

    @Test
    void asistenciaSoloDesdeElInicioYSobreActivos() {
        Turno enInicio = turno(EstadoTurno.CONFIRMADO, RELOJ.instant());
        assertThat(TransicionesTurno.marcarAsistencia(enInicio, EstadoTurno.ASISTIO, RELOJ)).isTrue();

        assertThatThrownBy(() -> TransicionesTurno.marcarAsistencia(
                turno(EstadoTurno.PENDIENTE, INICIO), EstadoTurno.AUSENTE, RELOJ))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("TRANSICION_INVALIDA");
                    assertThat(e.getMessage()).isEqualTo(
                            "Solo se puede marcar la asistencia desde la hora de inicio del turno.");
                });

        assertThatThrownBy(() -> TransicionesTurno.marcarAsistencia(
                turno(EstadoTurno.CANCELADO, RELOJ.instant()), EstadoTurno.ASISTIO, RELOJ))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("TRANSICION_INVALIDA");
                });
    }

    @Test
    void reprogramarSoloSobreActivos() {
        assertThat(TransicionesTurno.reprogramar(turno(EstadoTurno.PENDIENTE, INICIO))).isTrue();
        assertThat(TransicionesTurno.reprogramar(turno(EstadoTurno.CONFIRMADO, INICIO))).isTrue();

        assertThatThrownBy(() -> TransicionesTurno.reprogramar(turno(EstadoTurno.AUSENTE, INICIO)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("TRANSICION_INVALIDA");
                    assertThat(e.getMessage()).isEqualTo("Ese cambio de estado no está permitido para este turno.");
                });
    }

    private static Turno turno(EstadoTurno estado, Instant inicio) {
        return new Turno(new Paciente("Ana", "Ejemplo", "99999999"), new Prestacion("Consulta", 30),
                inicio, inicio.plus(Duration.ofMinutes(30)), estado, "tokenFicticioDePruebaConLongitudValida",
                "paciente@ejemplo.com", "+54 9 11 0000-0000");
    }
}
