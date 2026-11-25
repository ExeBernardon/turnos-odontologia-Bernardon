package ar.turnos.odontologia.bloqueo;

import java.util.List;

/** BloqueoPrevisualizacion del contrato. */
public record BloqueoPrevisualizacionResponse(List<TurnoAfectadoResponse> turnosAfectados) {
}
