package ar.turnos.odontologia.paciente;

/** Contacto (email y telefono) del ultimo turno creado de un paciente; solo lectura. */
public record ContactoResponse(String email, String telefono) {
}
