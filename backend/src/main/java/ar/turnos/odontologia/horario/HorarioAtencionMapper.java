package ar.turnos.odontologia.horario;

/** Mapeo manual entidad a DTO: las entidades JPA nunca se exponen. */
final class HorarioAtencionMapper {

    private HorarioAtencionMapper() {
    }

    static HorarioAtencionResponse aRespuesta(HorarioAtencion h) {
        return new HorarioAtencionResponse(h.getId(), h.getDiaSemana().getValue(), h.getHoraInicio(),
                h.getHoraFin());
    }
}
