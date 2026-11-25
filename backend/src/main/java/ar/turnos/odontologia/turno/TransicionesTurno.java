package ar.turnos.odontologia.turno;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;

import ar.turnos.odontologia.common.ApiException;

/**
 * Máquina de estados del turno (KB 04, RN-TUR y RN-CAN). Los métodos solo validan y devuelven
 * {@code true} cuando la acción debe aplicarse; {@code false} significa idempotencia. No mutan el
 * turno para que el service pueda evaluar primero la transición y después el plazo sin efectos
 * parciales. Todo rechazo es 422 {@code TRANSICION_INVALIDA} con el mensaje del contrato.
 */
public final class TransicionesTurno {

    private static final String MSG_GENERAL = "Ese cambio de estado no está permitido para este turno.";
    private static final String MSG_CANCELADO =
            "El turno está cancelado y no se puede confirmar.";
    private static final String MSG_CONFIRMADO_PACIENTE =
            "Tu turno ya está confirmado. Para cancelarlo comunicate con el consultorio.";
    private static final String MSG_CANCELADO_O_TERMINAL =
            "El turno ya está cancelado o finalizado y no se puede cancelar.";
    private static final String MSG_ASISTENCIA =
            "Solo se puede marcar la asistencia desde la hora de inicio del turno.";

    private TransicionesTurno() {
    }

    /** {@code true} si el turno está {@code PENDIENTE} y aún no empezó; {@code false} si ya está confirmado. */
    public static boolean confirmar(Turno turno, Clock reloj) {
        if (turno.getEstado() == EstadoTurno.CONFIRMADO) {
            return false;
        }
        if (turno.getEstado() == EstadoTurno.CANCELADO) {
            throw invalida(MSG_CANCELADO);
        }
        if (!activo(turno) || iniciado(turno, reloj)) {
            throw invalida(MSG_GENERAL);
        }
        return true;
    }

    /**
     * {@code true} si hay que cancelar; {@code false} si el paciente ya lo canceló (idempotencia).
     * El odontólogo nunca es idempotente: sobre cancelado o terminal responde 422.
     */
    public static boolean cancelar(Turno turno, CanceladoPor actor) {
        if (turno.getEstado() == EstadoTurno.CANCELADO && actor == CanceladoPor.PACIENTE) {
            return false;
        }
        if (activo(turno)) {
            if (actor == CanceladoPor.PACIENTE && turno.getEstado() == EstadoTurno.CONFIRMADO) {
                throw invalida(MSG_CONFIRMADO_PACIENTE);
            }
            return true;
        }
        throw invalida(actor == CanceladoPor.PACIENTE
                ? MSG_GENERAL : MSG_CANCELADO_O_TERMINAL);
    }

    /** Solo {@code ASISTIO}/{@code AUSENTE} desde activos y a partir de la hora de inicio. */
    public static boolean marcarAsistencia(Turno turno, EstadoTurno destino, Clock reloj) {
        if (destino != EstadoTurno.ASISTIO && destino != EstadoTurno.AUSENTE) {
            throw invalida(MSG_GENERAL);
        }
        if (!activo(turno)) {
            throw invalida(MSG_GENERAL);
        }
        if (turno.getInicio().isAfter(reloj.instant())) {
            throw invalida(MSG_ASISTENCIA);
        }
        return true;
    }

    /** Solo turnos activos: cancelados y terminales no se reprograman. */
    public static boolean reprogramar(Turno turno) {
        if (!activo(turno)) {
            throw invalida(MSG_GENERAL);
        }
        return true;
    }

    public static boolean activo(Turno turno) {
        return EstadoTurno.ACTIVOS.contains(turno.getEstado());
    }

    private static boolean iniciado(Turno turno, Clock reloj) {
        Instant ahora = reloj.instant();
        return !turno.getInicio().isAfter(ahora);
    }

    private static ApiException invalida(String mensaje) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TRANSICION_INVALIDA", mensaje);
    }
}
