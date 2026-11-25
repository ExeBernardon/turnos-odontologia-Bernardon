package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Formato del request publico: 400 con detalles, pero el consentimiento NO es un 400. */
class ReservaPublicaRequestTest {

    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-12-10T10:00:00-03:00");

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void unRequestValidoNoTieneViolaciones() {
        assertThat(violaciones(requestValido())).isEmpty();
    }

    @Test
    void losCamposObligatoriosSeValidan() {
        ReservaPublicaRequest request = new ReservaPublicaRequest(
                null, null, null, null, null, null, null, true);

        assertThat(violaciones(request)).containsExactlyInAnyOrder(
                "nombre", "apellido", "dni", "email", "telefono", "prestacionId", "inicio");
    }

    @Test
    void losFormatosInvalidosSeValidan() {
        ReservaPublicaRequest request = new ReservaPublicaRequest(
                "Ana", "Ejemplo", "99.999.99A", "no-es-email", "123", 1L,
                INICIO, true);

        assertThat(violaciones(request)).contains("dni", "email", "telefono");
    }

    @Test
    void elConsentimientoNoEsUnErrorDeFormato() {
        assertThat(violaciones(new ReservaPublicaRequest("Ana", "Ejemplo", "99.999.999",
                "ana@ejemplo.com", "+54 9 11 0000-0000", 1L, INICIO, false))).isEmpty();
        assertThat(violaciones(new ReservaPublicaRequest("Ana", "Ejemplo", "99.999.999",
                "ana@ejemplo.com", "+54 9 11 0000-0000", 1L, INICIO, null))).isEmpty();
    }

    @Test
    void elInicioExigeOffsetExplicito() throws Exception {
        ReservaPublicaRequest request = mapper.readValue(
                "{\"nombre\":\"Ana\",\"apellido\":\"Ejemplo\",\"dni\":\"99999999\","
                        + "\"email\":\"ana@ejemplo.com\",\"telefono\":\"+54 9 11 0000-0000\","
                        + "\"prestacionId\":1,\"inicio\":\"2026-12-10T10:00:00\",\"consentimiento\":true}",
                ReservaPublicaRequest.class);

        assertThat(request.inicio()).isNull();
        assertThat(violaciones(request)).containsExactly("inicio");
    }

    private Set<String> violaciones(ReservaPublicaRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private ReservaPublicaRequest requestValido() {
        return new ReservaPublicaRequest("Ana", "Ejemplo", "99.999.999", "ana@ejemplo.com",
                "+54 9 11 0000-0000", 1L, INICIO, true);
    }
}
