package ar.turnos.odontologia.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class LoginRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> camposInvalidos(LoginRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void aceptaEmailYPasswordValidos() {
        assertThat(camposInvalidos(new LoginRequest("odontologo@ejemplo.com", "clave-ficticia"))).isEmpty();
    }

    @Test
    void normalizaElEmailConEspaciosYMayusculasAntesDeValidar() {
        LoginRequest request = new LoginRequest("  Odontologo@Ejemplo.com  ", "clave-ficticia");

        assertThat(request.email()).isEqualTo("odontologo@ejemplo.com");
        assertThat(camposInvalidos(request)).isEmpty();
    }

    @Test
    void rechazaEmailVacioMalformadoYPasswordVacio() {
        assertThat(camposInvalidos(new LoginRequest("", "clave-ficticia"))).contains("email");
        assertThat(camposInvalidos(new LoginRequest("no-es-email", "clave-ficticia"))).contains("email");
        assertThat(camposInvalidos(new LoginRequest("odontologo@ejemplo.com", " "))).contains("password");
    }
}
