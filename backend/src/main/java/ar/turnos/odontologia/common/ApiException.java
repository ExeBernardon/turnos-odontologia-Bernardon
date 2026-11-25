package ar.turnos.odontologia.common;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * Base de las excepciones de negocio: cada dominio la lanza (o la extiende) con un status y un
 * {@code codigo} del catalogo de docs/api/errores.md, y {@link GlobalExceptionHandler} arma el JSON.
 * Los {@code detalles} solo se usan en 400 VALIDACION.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;
    private final List<DetalleError> detalles;

    public ApiException(HttpStatus status, String codigo, String mensaje) {
        this(status, codigo, mensaje, List.of());
    }

    public ApiException(HttpStatus status, String codigo, String mensaje, List<DetalleError> detalles) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
        this.detalles = detalles == null ? List.of() : List.copyOf(detalles);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

    public List<DetalleError> getDetalles() {
        return detalles;
    }
}
