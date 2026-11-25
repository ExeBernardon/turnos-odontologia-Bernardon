package ar.turnos.odontologia.paciente;

/** Forma del paciente segun docs/api/openapi.yaml; {@code ultimoContacto} siempre presente (puede ser null). */
public record PacienteResponse(Long id, String nombre, String apellido, String dni, ContactoResponse ultimoContacto) {
}
