package ar.turnos.odontologia.turno;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;

import ar.turnos.odontologia.common.ApiException;

/**
 * Plazos de las acciones del paciente, calculados con el {@link Clock} inyectado para que los bordes
 * sean deterministas en tests. Ambos umbrales son inclusivos (RN-TOK-05, RN-CAN-02).
 */
public final class PlazosTurno {

    static final Duration MAXIMO_CONFIRMACION = Duration.ofHours(48);
    static final Duration MINIMO_CANCELACION = Duration.ofHours(24);

    static final String MSG_CONFIRMACION_FUERA_DE_PLAZO =
            "Todavía no se puede confirmar el turno. Podés confirmarlo desde 48 h antes.";
    static final String MSG_CANCELACION_FUERA_DE_PLAZO =
            "Ya no se puede cancelar desde el link (faltan menos de 24 h). Comunicate con el consultorio.";

    private PlazosTurno() {
    }

    /** El paciente confirma desde 48 h antes y siempre antes del inicio. */
    public static boolean confirmablePorPaciente(Instant inicio, Clock reloj) {
        return inicio.isAfter(reloj.instant())
                && anticipacion(inicio, reloj).compareTo(MAXIMO_CONFIRMACION) <= 0;
    }

    /** El paciente cancela por link con 24 h o más de anticipación. */
    public static boolean cancelablePorPaciente(Instant inicio, Clock reloj) {
        return anticipacion(inicio, reloj).compareTo(MINIMO_CANCELACION) >= 0;
    }

    public static void exigirConfirmable(Instant inicio, Clock reloj) {
        if (!confirmablePorPaciente(inicio, reloj)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "CONFIRMACION_FUERA_DE_PLAZO", MSG_CONFIRMACION_FUERA_DE_PLAZO);
        }
    }

    public static void exigirCancelable(Instant inicio, Clock reloj) {
        if (!cancelablePorPaciente(inicio, reloj)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "CANCELACION_FUERA_DE_PLAZO", MSG_CANCELACION_FUERA_DE_PLAZO);
        }
    }

    private static Duration anticipacion(Instant inicio, Clock reloj) {
        return Duration.between(reloj.instant(), inicio);
    }
}
