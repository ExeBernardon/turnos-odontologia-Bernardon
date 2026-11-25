package ar.turnos.odontologia.disponibilidad;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Un día del schema `DiaDisponible`: fecha local e inicios con offset de la zona del consultorio. */
public record DiaDisponibleResponse(LocalDate fecha, List<OffsetDateTime> horarios) {
}
