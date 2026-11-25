package ar.turnos.odontologia.disponibilidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Verifica que el JSON serializado coincide con los ejemplos del contrato, clave por clave. */
class DisponibilidadSerializacionTest {

    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void laSerializacionCoincideConElEjemploPublico() throws Exception {
        DisponibilidadResponse respuesta = respuesta();

        String cuerpo = json.writeValueAsString(respuesta);
        JsonNode actual = json.readTree(cuerpo);
        JsonNode esperado = json.readTree(
                Files.readString(Path.of("..", "docs", "api", "ejemplos", "disponibilidad-publica.200.json")));

        assertThat(actual).isEqualTo(esperado);
        assertThat(cuerpo).contains("2026-12-01T09:00:00-03:00");
        assertThat(cuerpo).doesNotContain("paciente", "token", "email");
    }

    @Test
    void laSerializacionCoincideConElEjemploPrivado() throws Exception {
        DisponibilidadResponse respuesta = new DisponibilidadResponse(
                new PrestacionRefResponse(1L, "Consulta", 30),
                List.of(new DiaDisponibleResponse(LocalDate.of(2026, 12, 1), List.of(
                        OffsetDateTimeUtil.de(2026, 12, 1, 9, 0),
                        OffsetDateTimeUtil.de(2026, 12, 1, 9, 30)))));

        JsonNode actual = json.readTree(json.writeValueAsString(respuesta));
        JsonNode esperado = json.readTree(
                Files.readString(Path.of("..", "docs", "api", "ejemplos", "disponibilidad-privada.200.json")));

        assertThat(actual).isEqualTo(esperado);
    }

    private static DisponibilidadResponse respuesta() {
        return new DisponibilidadResponse(
                new PrestacionRefResponse(1L, "Consulta", 30),
                List.of(
                        new DiaDisponibleResponse(LocalDate.of(2026, 12, 1), List.of(
                                OffsetDateTimeUtil.de(2026, 12, 1, 9, 0),
                                OffsetDateTimeUtil.de(2026, 12, 1, 9, 30),
                                OffsetDateTimeUtil.de(2026, 12, 1, 10, 0))),
                        new DiaDisponibleResponse(LocalDate.of(2026, 12, 2), List.of())));
    }

    private static final class OffsetDateTimeUtil {
        private OffsetDateTimeUtil() {
        }

        static java.time.OffsetDateTime de(int anio, int mes, int dia, int hora, int minuto) {
            return java.time.OffsetDateTime.of(anio, mes, dia, hora, minuto, 0, 0, ZoneOffset.ofHours(-3));
        }
    }
}
