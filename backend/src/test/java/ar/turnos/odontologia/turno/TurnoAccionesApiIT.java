package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** Confirmar, cancelar y marcar asistencia desde la agenda, con JWT y aviso de cancelación. */
class TurnoAccionesApiIT extends TurnosApiITBase {

    @Test
    void confirmarSinElLimiteDe48HorasEsIdempotenteYSinMails() throws Exception {
        Turno turno = guardar(en(240), EstadoTurno.PENDIENTE, "odontologo-confirmar");

        autenticado(post("/api/turnos/{id}/confirmar", turno.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));
        autenticado(post("/api/turnos/{id}/confirmar", turno.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));

        assertThat(turnos.findById(turno.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);
        verifyNoInteractions(notificaciones);
    }

    @Test
    void confirmarCanceladoOTerminalEs422() throws Exception {
        Turno cancelado = guardar(en(36), EstadoTurno.CANCELADO, "odontologo-confirmar-cancelado");
        String cuerpo = autenticado(post("/api/turnos/{id}/confirmar", cancelado.getId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"))
                .andReturn().getResponse().getContentAsString();
        assertMensaje(cuerpo, "El turno está cancelado y no se puede confirmar.");

        Turno terminal = guardar(en(36), EstadoTurno.AUSENTE, "odontologo-confirmar-terminal");
        autenticado(post("/api/turnos/{id}/confirmar", terminal.getId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
    }

    @Test
    void confirmarInexistenteEs404YSinJwtEs401() throws Exception {
        autenticado(post("/api/turnos/{id}/confirmar", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));

        mvc.perform(post("/api/turnos/{id}/confirmar", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void cancelarConfirmadoCercaDelInicioRegistraOdontologoYPideAviso() throws Exception {
        Turno turno = guardar(AHORA.minus(Duration.ofMinutes(90)), EstadoTurno.CONFIRMADO,
                "odontologo-cancelar");

        autenticado(post("/api/turnos/{id}/cancelar", turno.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.canceladoPor").value("ODONTOLOGO"));

        Turno actualizado = turnos.findById(turno.getId()).orElseThrow();
        assertThat(actualizado.getEstado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(actualizado.getCanceladoPor()).isEqualTo(CanceladoPor.ODONTOLOGO);
        verify(notificaciones).cancelacionPorOdontologo(argThat(t ->
                t.getEmailContacto().equals("paciente@ejemplo.com")
                        && t.getEstado() == EstadoTurno.CANCELADO));
    }

    @Test
    void cancelarYaCanceladoOTerminalEs422YNuncaEsIdempotente() throws Exception {
        Turno cancelado = guardar(en(36), EstadoTurno.CANCELADO, "odontologo-cancelar-cancelado");
        String cuerpo = autenticado(post("/api/turnos/{id}/cancelar", cancelado.getId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"))
                .andReturn().getResponse().getContentAsString();
        assertMensaje(cuerpo, "El turno ya está cancelado o finalizado y no se puede cancelar.");

        Turno terminal = guardar(en(36), EstadoTurno.ASISTIO, "odontologo-cancelar-terminal");
        autenticado(post("/api/turnos/{id}/cancelar", terminal.getId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        verifyNoInteractions(notificaciones);
    }

    @Test
    void falloDelAvisoNoRevierteLaCancelacion() throws Exception {
        Turno turno = guardar(en(2), EstadoTurno.PENDIENTE, "odontologo-cancelar-fallo-aviso");
        org.mockito.Mockito.when(notificaciones.cancelacionPorOdontologo(any(Turno.class))).thenReturn(false);

        autenticado(post("/api/turnos/{id}/cancelar", turno.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        assertThat(turnos.findById(turno.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.CANCELADO);
        verify(notificaciones).cancelacionPorOdontologo(any(Turno.class));
    }

    @Test
    void cancelarInexistenteEs404YSinJwtEs401() throws Exception {
        autenticado(post("/api/turnos/{id}/cancelar", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));

        mvc.perform(post("/api/turnos/{id}/cancelar", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void asistenciaDesdeElInicioExactoMarcaElEstadoTerminal() throws Exception {
        Turno turno = guardar(AHORA, EstadoTurno.CONFIRMADO, "asistio");

        autenticado(patch("/api/turnos/{id}/estado", turno.getId())
                .contentType(jsonType()).content("{\"estado\":\"ASISTIO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ASISTIO"));

        assertThat(turnos.findById(turno.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.ASISTIO);
        verifyNoInteractions(notificaciones);
    }

    @Test
    void ausenteAntesDelInicioEs422YSinEfectos() throws Exception {
        Turno turno = guardar(AHORA.plus(Duration.ofMinutes(1)), EstadoTurno.PENDIENTE, "ausente-antes");

        String cuerpo = autenticado(patch("/api/turnos/{id}/estado", turno.getId())
                .contentType(jsonType()).content("{\"estado\":\"AUSENTE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"))
                .andReturn().getResponse().getContentAsString();
        assertMensaje(cuerpo, "Solo se puede marcar la asistencia desde la hora de inicio del turno.");

        assertThat(turnos.findById(turno.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
    }

    @Test
    void asistenciaSobreCanceladoEs422YEstadoInvalidoEnElBodyEs400() throws Exception {
        Turno cancelado = guardar(AHORA, EstadoTurno.CANCELADO, "asistencia-cancelado");
        autenticado(patch("/api/turnos/{id}/estado", cancelado.getId())
                .contentType(jsonType()).content("{\"estado\":\"ASISTIO\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));

        Turno activo = guardar(AHORA, EstadoTurno.CONFIRMADO, "asistencia-body-invalido");
        autenticado(patch("/api/turnos/{id}/estado", activo.getId())
                .contentType(jsonType()).content("{\"estado\":\"PENDIENTE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void asistenciaSinJwtEs401YInexistenteEs404() throws Exception {
        mvc.perform(patch("/api/turnos/{id}/estado", 1)
                .contentType(jsonType()).content("{\"estado\":\"ASISTIO\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        autenticado(patch("/api/turnos/{id}/estado", 999999)
                .contentType(jsonType()).content("{\"estado\":\"ASISTIO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }
}
