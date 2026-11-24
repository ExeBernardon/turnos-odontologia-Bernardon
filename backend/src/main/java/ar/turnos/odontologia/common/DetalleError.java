package ar.turnos.odontologia.common;

/** Detalle de un campo invalido (solo en 400 VALIDACION). {@code campo} va en camelCase. */
public record DetalleError(String campo, String mensaje) {
}
