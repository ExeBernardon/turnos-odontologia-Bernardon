package ar.turnos.odontologia.bloqueo;

import java.util.List;

/** BloqueoCreado del contrato. */
public record BloqueoCreadoResponse(BloqueoResponse bloqueo, List<TurnoAfectadoResponse> turnosCancelados) {
}
