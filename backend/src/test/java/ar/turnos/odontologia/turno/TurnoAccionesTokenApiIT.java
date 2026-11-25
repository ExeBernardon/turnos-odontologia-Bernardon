package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** Acciones del paciente por token: plazos inclusivos, idempotencia y orden de evaluación. */
class TurnoAccionesTokenApiIT extends TurnosApiITBase {

    @Test
    void confirmarDentroDeLaVentanaCambiaElEstadoYDevuelveElResumenPublico() throws Exception {
        Turno turno = guardar(en(36), EstadoTurno.PENDIENTE, "confirmar-36");

        mvc.perform(post("/api/public/turnos/{token}/confirmar", turno.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.puedeConfirmar").value(false))
                .andExpect(jsonPath("$.puedeCancelar").value(false))
                .andExpect(jsonPath("$.nombrePaciente").value("Ana"))
                .andExpect(jsonPath("$.token").doesNotExist());

        assertThat(turnos.findById(turno.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);
        verifyNoInteractions(notificaciones);
    }

    @Test
    void confirmarEnElBordeExactoDe48HorasEstaDentroDelPlazo() throws Exception {
        Turno turno = guardar(en(48), EstadoTurno.PENDIENTE, "confirmar-48");

        mvc.perform(post("/api/public/turnos/{token}/confirmar", turno.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));

        assertThat(turnos.findById(turno.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);
    }

    @Test
    void confirmarConMasDe48HorasEs422YNoCambiaElEstado() throws Exception {
        Turno turno = guardar(en(49), EstadoTurno.PENDIENTE, "confirmar-49");

        String cuerpo = mvc.perform(post("/api/public/turnos/{token}/confirmar", turno.getToken()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONFIRMACION_FUERA_DE_PLAZO"))
                .andReturn().getResponse().getContentAsString();
        assertMensaje(cuerpo,
                "Todavía no se puede confirmar el turno. Podés confirmarlo desde 48 h antes.");

        assertThat(turnos.findById(turno.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
    }

    @Test
    void confirmarTokenInexistenteEs404SinRevelarDatos() throws Exception {
        mvc.perform(post("/api/public/turnos/{token}/confirmar", "token-inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value("El link no es válido."));
    }

    @Test
    void confirmarDosVecesEsIdempotenteYSinMails() throws Exception {
        Turno turno = guardar(en(36), EstadoTurno.PENDIENTE, "confirmar-idempotente");

        mvc.perform(post("/api/public/turnos/{token}/confirmar", turno.getToken()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/public/turnos/{token}/confirmar", turno.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));

        verifyNoInteractions(notificaciones);
    }

    @Test
    void confirmarCanceladoConMasDe48HorasDaTransicionInvalidaAntesQuePlazo() throws Exception {
        Turno turno = guardar(en(72), EstadoTurno.CANCELADO, "confirmar-cancelado");
        turno.setCanceladoPor(CanceladoPor.PACIENTE);
        turnos.saveAndFlush(turno);

        String cuerpo = mvc.perform(post("/api/public/turnos/{token}/confirmar", turno.getToken()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"))
                .andReturn().getResponse().getContentAsString();
        assertMensaje(cuerpo, "El turno está cancelado y no se puede confirmar.");
    }

    @Test
    void cancelarDentroDelPlazoCambiaElEstadoYCanceladoPorPaciente() throws Exception {
        Turno turno = guardar(en(30), EstadoTurno.PENDIENTE, "cancelar-30");

        mvc.perform(post("/api/public/turnos/{token}/cancelar", turno.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.puedeCancelar").value(false));

        Turno actualizado = turnos.findById(turno.getId()).orElseThrow();
        assertThat(actualizado.getEstado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(actualizado.getCanceladoPor()).isEqualTo(CanceladoPor.PACIENTE);
        verifyNoInteractions(notificaciones);
    }

    @Test
    void cancelarEnElBordeExactoDe24HorasEstaDentroDelPlazo() throws Exception {
        Turno turno = guardar(en(24), EstadoTurno.PENDIENTE, "cancelar-24");

        mvc.perform(post("/api/public/turnos/{token}/cancelar", turno.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));
    }

    @Test
    void cancelarConMenosDe24HorasEs422YNoCambiaElEstado() throws Exception {
        Turno turno = guardar(en(23), EstadoTurno.PENDIENTE, "cancelar-23");

        String cuerpo = mvc.perform(post("/api/public/turnos/{token}/cancelar", turno.getToken()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CANCELACION_FUERA_DE_PLAZO"))
                .andReturn().getResponse().getContentAsString();
        assertMensaje(cuerpo,
                "Ya no se puede cancelar desde el link (faltan menos de 24 h). Comunicate con el consultorio.");

        assertThat(turnos.findById(turno.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
    }

    @Test
    void cancelarConfirmadoEs422ConElMensajeDeConfirmacionDefinitiva() throws Exception {
        Turno turno = guardar(en(30), EstadoTurno.CONFIRMADO, "cancelar-confirmado");

        String cuerpo = mvc.perform(post("/api/public/turnos/{token}/cancelar", turno.getToken()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"))
                .andReturn().getResponse().getContentAsString();
        assertMensaje(cuerpo,
                "Tu turno ya está confirmado. Para cancelarlo comunicate con el consultorio.");

        assertThat(turnos.findById(turno.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);
    }

    @Test
    void cancelarYaCanceladoConMenosDe24HorasEsIdempotenteAntesQueElPlazo() throws Exception {
        Turno turno = guardar(en(2), EstadoTurno.CANCELADO, "cancelar-idempotente");
        turno.setCanceladoPor(CanceladoPor.PACIENTE);
        turnos.saveAndFlush(turno);

        mvc.perform(post("/api/public/turnos/{token}/cancelar", turno.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        verifyNoInteractions(notificaciones);
    }

    @Test
    void cancelarTerminalEs422YSinEfectos() throws Exception {
        Turno turno = guardar(en(2), EstadoTurno.ASISTIO, "cancelar-terminal");

        mvc.perform(post("/api/public/turnos/{token}/cancelar", turno.getToken()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value("Ese cambio de estado no está permitido para este turno."));

        assertThat(turnos.findById(turno.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.ASISTIO);
    }

    @Test
    void cancelarTokenInexistenteEs404() throws Exception {
        mvc.perform(post("/api/public/turnos/{token}/cancelar", "token-inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
    }
}
