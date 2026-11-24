package ar.turnos.odontologia.turno;

import java.util.EnumSet;
import java.util.Set;

/** Estados del turno (KB 04). Se persiste como texto. */
public enum EstadoTurno {
    PENDIENTE, CONFIRMADO, CANCELADO, ASISTIO, AUSENTE;

    /**
     * Estados que ocupan el horario. Debe coincidir con el WHERE de la restriccion
     * {@code turno_sin_solapamiento} en schema-extra.sql.
     */
    public static final Set<EstadoTurno> ACTIVOS = EnumSet.of(PENDIENTE, CONFIRMADO);
}
