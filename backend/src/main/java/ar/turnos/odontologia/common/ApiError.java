package ar.turnos.odontologia.common;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Formato uniforme de error de la API (docs/api/errores.md). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String codigo,
        String mensaje,
        String path,
        List<DetalleError> detalles) {
}
