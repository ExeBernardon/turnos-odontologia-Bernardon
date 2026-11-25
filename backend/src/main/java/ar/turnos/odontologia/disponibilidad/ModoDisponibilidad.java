package ar.turnos.odontologia.disponibilidad;

/**
 * Contexto de una consulta o validación de horarios (RN-DIS-06). La reserva pública aplica la
 * ventana de 2 h a 60 días; la consulta del odontólogo solo exige no estar en el pasado.
 */
public enum ModoDisponibilidad {
    PUBLICA, ODONTOLOGO
}
