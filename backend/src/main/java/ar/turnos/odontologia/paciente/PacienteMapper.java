package ar.turnos.odontologia.paciente;

/** Mapeo manual entidad a DTO: las entidades JPA nunca se exponen. */
final class PacienteMapper {

    private PacienteMapper() {
    }

    static PacienteResponse aRespuesta(Paciente p, ContactoResponse ultimoContacto) {
        return new PacienteResponse(p.getId(), p.getNombre(), p.getApellido(), p.getDni(), ultimoContacto);
    }
}
