package ar.turnos.odontologia.disponibilidad;

/**
 * Contexto con el que se valida un horario. {@code turnoExcluidoId} se usa al reprogramar para que
 * el turno propio no se cuente entre los ocupantes.
 */
public record ContextoValidacion(ModoDisponibilidad modo, Long turnoExcluidoId) {

    public ContextoValidacion {
        if (modo == null) {
            throw new IllegalArgumentException("El modo de disponibilidad es obligatorio");
        }
    }

    public static ContextoValidacion publica() {
        return new ContextoValidacion(ModoDisponibilidad.PUBLICA, null);
    }

    public static ContextoValidacion odontologo() {
        return new ContextoValidacion(ModoDisponibilidad.ODONTOLOGO, null);
    }
}
