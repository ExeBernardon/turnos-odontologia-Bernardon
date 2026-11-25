package ar.turnos.odontologia.prestacion;

/** Prestacion para la reserva publica: solo lo que el paciente necesita ver. */
public record PrestacionPublicaResponse(Long id, String nombre, int duracionMinutos) {
}
