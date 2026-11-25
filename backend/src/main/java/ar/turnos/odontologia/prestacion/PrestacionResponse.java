package ar.turnos.odontologia.prestacion;

/** Prestacion del contrato (vista del odontologo). */
public record PrestacionResponse(Long id, String nombre, int duracionMinutos, boolean activa) {
}
