package ar.turnos.odontologia.paciente;

/** Proyeccion del contacto (email y telefono) del ultimo turno creado de un paciente. */
public interface ContactoUltimoTurno {

    Long getPacienteId();

    String getEmail();

    String getTelefono();
}
