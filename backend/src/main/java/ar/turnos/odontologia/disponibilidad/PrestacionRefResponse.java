package ar.turnos.odontologia.disponibilidad;

/** Referencia de la prestación en el schema `PrestacionRef` del contrato (sin exponer la entidad). */
public record PrestacionRefResponse(Long id, String nombre, int duracionMinutos) {
}
