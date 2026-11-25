package ar.turnos.odontologia.bloqueo;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ar.turnos.odontologia.common.ApiErrorFactory;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Arma el 409 con {@code turnosAfectados}, que el error uniforme no expresa. Acotado a este controller y con
 * prioridad maxima para ganarle al handler generico de {@code ApiException}.
 */
@RestControllerAdvice(assignableTypes = BloqueoController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class BloqueoExceptionAdvice {

    private final ApiErrorFactory errores;

    BloqueoExceptionAdvice(ApiErrorFactory errores) {
        this.errores = errores;
    }

    @ExceptionHandler(BloqueoConTurnosAfectadosException.class)
    ResponseEntity<BloqueoConflictoResponse> conTurnosAfectados(BloqueoConTurnosAfectadosException e,
            HttpServletRequest req) {
        var base = errores.crear(e.getStatus(), e.getCodigo(), e.getMessage(), req.getRequestURI());
        return ResponseEntity.status(e.getStatus())
                .body(BloqueoConflictoResponse.de(base, e.getTurnosAfectados()));
    }
}
