package ar.turnos.odontologia.common;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ar.turnos.odontologia.config.AppProperties;

/** Arma el {@link ApiError} con timestamp en la zona de negocio. Lo usan el handler y el entry point. */
@Component
public class ApiErrorFactory {

    private final Clock clock;
    private final AppProperties app;

    public ApiErrorFactory(Clock clock, AppProperties app) {
        this.clock = clock;
        this.app = app;
    }

    public ApiError crear(HttpStatus status, String codigo, String mensaje, String path, List<DetalleError> detalles) {
        OffsetDateTime ahora = OffsetDateTime.now(clock.withZone(app.zonaHoraria()));
        return new ApiError(ahora, status.value(), status.getReasonPhrase(), codigo, mensaje, path, detalles);
    }

    public ApiError crear(HttpStatus status, String codigo, String mensaje, String path) {
        return crear(status, codigo, mensaje, path, null);
    }
}
