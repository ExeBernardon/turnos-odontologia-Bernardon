package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** El resumen publico no puede filtrar datos personales ni el token (RN-TOK-02). */
class TurnoPublicoResponseTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void serializaSoloLosCamposDelContrato() throws Exception {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-12-10T10:00:00-03:00");
        TurnoPublicoResponse respuesta = new TurnoPublicoResponse(
                new TurnoPublicoResponse.PrestacionResumen("Consulta", 30),
                inicio,
                inicio.plusMinutes(30),
                EstadoTurno.PENDIENTE,
                "Ana",
                false,
                true,
                "Consultorio de ejemplo - Tel. +54 9 11 0000-0000");

        String json = mapper.writeValueAsString(respuesta);

        assertThat(json).contains("\"nombrePaciente\":\"Ana\"")
                .contains("\"puedeConfirmar\":false")
                .contains("\"puedeCancelar\":true")
                .contains("\"duracionMinutos\":30")
                .contains("\"estado\":\"PENDIENTE\"");
        assertThat(json).doesNotContain("dni", "apellido", "email", "telefono", "token", "\"id\"");
    }
}
