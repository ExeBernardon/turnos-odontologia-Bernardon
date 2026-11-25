package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Formato de los bodies de reprogramar y marcar asistencia (400 VALIDACION con detalles). */
class AccionesTurnoRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void reprogramarValidoNoTieneViolaciones() throws Exception {
        ReprogramarRequest request = mapper.readValue(
                "{\"inicio\":\"2026-12-01T18:00:00-03:00\"}", ReprogramarRequest.class);

        assertThat(request.inicio()).isNotNull();
        assertThat(violaciones(request)).isEmpty();
    }

    @Test
    void reprogramarSinInicioOSinOffsetTieneViolacionEnInicio() throws Exception {
        ReprogramarRequest sinInicio = mapper.readValue("{}", ReprogramarRequest.class);
        ReprogramarRequest sinOffset = mapper.readValue(
                "{\"inicio\":\"2026-12-01T18:00:00\"}", ReprogramarRequest.class);

        assertThat(violaciones(sinInicio)).containsExactly("inicio");
        assertThat(sinOffset.inicio()).isNull();
        assertThat(violaciones(sinOffset)).containsExactly("inicio");
    }

    @Test
    void asistenciaValidaNoTieneViolaciones() throws Exception {
        EstadoAsistenciaRequest request = mapper.readValue(
                "{\"estado\":\"ASISTIO\"}", EstadoAsistenciaRequest.class);

        assertThat(violaciones(request)).isEmpty();
    }

    @Test
    void asistenciaRechazaAusenciaDelCampoYEstadosNoTerminales() {
        assertThat(violaciones(new EstadoAsistenciaRequest(null)))
                .containsExactlyInAnyOrder("estado", "estadoPermitido");
        assertThat(violaciones(new EstadoAsistenciaRequest(EstadoTurno.PENDIENTE)))
                .containsExactly("estadoPermitido");
    }

    private Set<String> violaciones(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
