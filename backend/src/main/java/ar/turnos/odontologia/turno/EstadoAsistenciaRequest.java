package ar.turnos.odontologia.turno;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * Request de asistencia (contrato C-01): el único estado válido es terminal de asistencia.
 * Se valida con {@code @AssertTrue} para responder 400 VALIDACION y no depender del mensaje de
 * deserialización del enum.
 */
public record EstadoAsistenciaRequest(@NotNull EstadoTurno estado) {

    @AssertTrue(message = "El estado debe ser ASISTIO o AUSENTE")
    @JsonIgnore
    public boolean getEstadoPermitido() {
        return estado == EstadoTurno.ASISTIO || estado == EstadoTurno.AUSENTE;
    }
}
