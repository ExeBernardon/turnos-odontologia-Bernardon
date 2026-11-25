package ar.turnos.odontologia.disponibilidad;

/**
 * Resultado de validar un inicio. Cuando no es válido, {@code motivo} es apto para mostrar al
 * usuario y se usa como {@code mensaje} del 422 {@code HORARIO_NO_DISPONIBLE}.
 */
public record ResultadoValidacion(boolean valido, String motivo) {

    private static final ResultadoValidacion VALIDO = new ResultadoValidacion(true, null);

    public static ResultadoValidacion aceptado() {
        return VALIDO;
    }

    public static ResultadoValidacion invalido(String motivo) {
        return new ResultadoValidacion(false, motivo);
    }
}
