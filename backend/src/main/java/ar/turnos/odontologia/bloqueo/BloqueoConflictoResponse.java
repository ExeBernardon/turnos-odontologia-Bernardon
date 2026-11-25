package ar.turnos.odontologia.bloqueo;

import java.time.OffsetDateTime;
import java.util.List;

import ar.turnos.odontologia.common.ApiError;

/** ErrorBloqueoConflicto del contrato: el error uniforme mas {@code turnosAfectados}. */
public record BloqueoConflictoResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String codigo,
        String mensaje,
        String path,
        List<TurnoAfectadoResponse> turnosAfectados) {

    static BloqueoConflictoResponse de(ApiError base, List<TurnoAfectadoResponse> turnosAfectados) {
        return new BloqueoConflictoResponse(base.timestamp(), base.status(), base.error(), base.codigo(),
                base.mensaje(), base.path(), turnosAfectados);
    }
}
