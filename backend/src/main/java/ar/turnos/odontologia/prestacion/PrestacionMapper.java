package ar.turnos.odontologia.prestacion;

/** Mapeo manual entidad a DTO: las entidades JPA nunca se exponen. */
final class PrestacionMapper {

    private PrestacionMapper() {
    }

    static PrestacionResponse aRespuesta(Prestacion p) {
        return new PrestacionResponse(p.getId(), p.getNombre(), p.getDuracionMinutos(), p.isActiva());
    }

    static PrestacionPublicaResponse aPublica(Prestacion p) {
        return new PrestacionPublicaResponse(p.getId(), p.getNombre(), p.getDuracionMinutos());
    }
}
