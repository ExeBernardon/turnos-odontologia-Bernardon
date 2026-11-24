package ar.turnos.odontologia.common;

import org.springframework.http.HttpStatus;

/**
 * Base de las excepciones de negocio: cada dominio la lanza (o la extiende) con un status y un
 * {@code codigo} del catalogo de docs/api/errores.md, y {@link GlobalExceptionHandler} arma el JSON.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public ApiException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }
}
