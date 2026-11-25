package ar.turnos.odontologia.paciente;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Validacion del request de pacientes reutilizado de C-07 (contrato: Dni / NombrePersona). */
class PacienteRequestTest {

    private static Validator validator;

    @BeforeAll
    static void crearValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static Set<String> camposInvalidos(PacienteRequest req) {
        return validator.validate(req).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void aceptaDniConPuntosYSinPuntos() {
        assertThat(camposInvalidos(new PacienteRequest("Carla", "Muestra", "99.999.997"))).isEmpty();
        assertThat(camposInvalidos(new PacienteRequest("Carla", "Muestra", "99999997"))).isEmpty();
        assertThat(camposInvalidos(new PacienteRequest("Carla", "Muestra", "9999999"))).isEmpty();
    }

    @Test
    void rechazaDniConGuionesOLetras() {
        assertThat(camposInvalidos(new PacienteRequest("Carla", "Muestra", "99-999-997"))).containsExactly("dni");
        assertThat(camposInvalidos(new PacienteRequest("Carla", "Muestra", "9999999A"))).containsExactly("dni");
    }

    @Test
    void rechazaNombreOApellidoEnBlanco() {
        assertThat(camposInvalidos(new PacienteRequest("  ", "Muestra", "99999997"))).containsExactly("nombre");
        assertThat(camposInvalidos(new PacienteRequest("Carla", "", "99999997"))).containsExactly("apellido");
    }
}
