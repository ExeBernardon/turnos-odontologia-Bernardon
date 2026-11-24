package ar.turnos.odontologia.common;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

/** Traduce las excepciones al formato uniforme de error del contrato (docs/api/errores.md). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String MSG_VALIDACION = "Hay campos con errores.";
    static final String MSG_MAL_FORMADA = "La solicitud no se pudo interpretar. Revisá el formato del cuerpo o de los parámetros.";
    static final String MSG_NO_ENCONTRADO = "No encontramos el recurso solicitado.";
    static final String MSG_ERROR_INTERNO = "Ocurrió un error inesperado. Intentá de nuevo en unos minutos.";

    private final ApiErrorFactory errores;

    public GlobalExceptionHandler(ApiErrorFactory errores) {
        this.errores = errores;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validacion(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<DetalleError> detalles = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new DetalleError(f.getField(), f.getDefaultMessage()))
                .toList();
        return responder(HttpStatus.BAD_REQUEST, "VALIDACION", MSG_VALIDACION, req, detalles);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> validacionDeParametros(HandlerMethodValidationException e, HttpServletRequest req) {
        List<DetalleError> detalles = e.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(err -> new DetalleError(r.getMethodParameter().getParameterName(), err.getDefaultMessage())))
                .toList();
        return responder(HttpStatus.BAD_REQUEST, "VALIDACION", MSG_VALIDACION, req, detalles);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HttpMediaTypeException.class })
    ResponseEntity<ApiError> solicitudMalFormada(Exception e, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, "SOLICITUD_MAL_FORMADA", MSG_MAL_FORMADA, req, null);
    }

    @ExceptionHandler({ NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class })
    ResponseEntity<ApiError> noEncontrado(Exception e, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", MSG_NO_ENCONTRADO, req, null);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> negocio(ApiException e, HttpServletRequest req) {
        return responder(e.getStatus(), e.getCodigo(), e.getMessage(), req, null);
    }

    /** Catch-all: el detalle va solo al log del servidor; la respuesta es generica. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> inesperado(Exception e, HttpServletRequest req) throws Exception {
        // La seguridad (401/403) la resuelven los filtros de Spring Security, no este handler.
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;
        }
        log.error("Error inesperado en {} {}", req.getMethod(), req.getRequestURI(), e);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", MSG_ERROR_INTERNO, req, null);
    }

    private ResponseEntity<ApiError> responder(HttpStatus status, String codigo, String mensaje,
            HttpServletRequest req, List<DetalleError> detalles) {
        return ResponseEntity.status(status)
                .body(errores.crear(status, codigo, mensaje, req.getRequestURI(), detalles));
    }
}
