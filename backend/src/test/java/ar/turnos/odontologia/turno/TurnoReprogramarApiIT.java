package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** Reprogramación: grilla, solapamiento, transiciones, umbral de 24 h y avisos. */
class TurnoReprogramarApiIT extends TurnosApiITBase {

    @Test
    void reprogramarAHorarioLibreRecalculaFinYConservaPacienteYPrestacion() throws Exception {
        Turno turno = guardar(en(30), EstadoTurno.PENDIENTE, "repro-valido");

        autenticado(patch("/api/turnos/{id}/reprogramar", turno.getId())
                .contentType(jsonType()).content(body(en(48))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").value(local(en(48))))
                .andExpect(jsonPath("$.fin").value(local(en(48).plus(Duration.ofMinutes(30)))))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.paciente.dni").value("99999999"))
                .andExpect(jsonPath("$.prestacion.nombre").value("Acciones IT"))
                .andExpect(jsonPath("$.token").doesNotExist());

        Turno actualizado = turnos.findById(turno.getId()).orElseThrow();
        assertThat(actualizado.getInicio()).isEqualTo(en(48));
        assertThat(actualizado.getFin()).isEqualTo(en(48).plus(Duration.ofMinutes(30)));
        assertThat(actualizado.isRecordatorioEnviado()).isFalse();
        assertThat(actualizado.isAlertaPendienteEnviada()).isFalse();
        verify(notificaciones).reprogramacion(any(Turno.class));
    }

    @Test
    void reprogramarSinOffsetEnElBodyEs400Validacion() throws Exception {
        Turno turno = guardar(en(30), EstadoTurno.PENDIENTE, "repro-sin-offset");

        autenticado(patch("/api/turnos/{id}/reprogramar", turno.getId())
                .contentType(jsonType()).content("{\"inicio\":\"2026-12-03T09:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[0].campo").value("inicio"));

        assertThat(turnos.findById(turno.getId()).orElseThrow().getInicio()).isEqualTo(en(30));
    }

    @Test
    void reprogramarSinJwtEs401YInexistenteEs404() throws Exception {
        mvc.perform(patch("/api/turnos/{id}/reprogramar", 1)
                .contentType(jsonType()).content(body(en(48))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        autenticado(patch("/api/turnos/{id}/reprogramar", 999999)
                .contentType(jsonType()).content(body(en(48))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void reprogramarFueraDelHorarioDeAtencionEs422YSinEfectos() throws Exception {
        Turno turno = guardar(en(30), EstadoTurno.PENDIENTE, "repro-fuera-atencion");

        String respuesta = autenticado(patch("/api/turnos/{id}/reprogramar", turno.getId())
                .contentType(jsonType()).content(body(en(23))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("HORARIO_NO_DISPONIBLE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(respuesta).get("mensaje").asText())
                .isEqualTo("Ese horario está fuera del horario de atención.");

        assertThat(turnos.findById(turno.getId()).orElseThrow().getInicio()).isEqualTo(en(30));
        verifyNoInteractions(notificaciones);
    }

    @Test
    void reprogramarOtroTurnoActivoEnElMismoHorarioEs422() throws Exception {
        Turno turno = guardar(en(30), EstadoTurno.PENDIENTE, "repro-ocupado-a");
        guardar(en(48), EstadoTurno.PENDIENTE, "repro-ocupado-b");

        autenticado(patch("/api/turnos/{id}/reprogramar", turno.getId())
                .contentType(jsonType()).content(body(en(48))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("HORARIO_NO_DISPONIBLE"));

        assertThat(turnos.findById(turno.getId()).orElseThrow().getInicio()).isEqualTo(en(30));
    }

    @Test
    void reprogramarCanceladoOTerminalEs422YSinAvisos() throws Exception {
        Turno cancelado = guardar(en(30), EstadoTurno.CANCELADO, "repro-cancelado");
        autenticado(patch("/api/turnos/{id}/reprogramar", cancelado.getId())
                .contentType(jsonType()).content(body(en(48))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));

        Turno terminal = guardar(en(30), EstadoTurno.ASISTIO, "repro-terminal");
        autenticado(patch("/api/turnos/{id}/reprogramar", terminal.getId())
                .contentType(jsonType()).content(body(en(48))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));

        assertThat(turnos.findById(cancelado.getId()).orElseThrow().getInicio()).isEqualTo(en(30));
        verifyNoInteractions(notificaciones);
    }

    @Test
    void reprogramarAMenosDe24HorasPasaAConfirmadoConFlagsYSinLinks() throws Exception {
        Turno turno = guardar(en(30), EstadoTurno.PENDIENTE, "repro-menos24");

        autenticado(patch("/api/turnos/{id}/reprogramar", turno.getId())
                .contentType(jsonType()).content(body(en(6))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));

        Turno actualizado = turnos.findById(turno.getId()).orElseThrow();
        assertThat(actualizado.getInicio()).isEqualTo(en(6));
        assertThat(actualizado.getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);
        assertThat(actualizado.isRecordatorioEnviado()).isTrue();
        assertThat(actualizado.isAlertaPendienteEnviada()).isTrue();
        verify(notificaciones).reprogramacionMenos24h(any(Turno.class));
    }

    @Test
    void reprogramarA24HorasOMasVuelveAPendienteYLimpiaFlags() throws Exception {
        Turno turno = guardar(en(6), EstadoTurno.CONFIRMADO, "repro-mas24");

        autenticado(patch("/api/turnos/{id}/reprogramar", turno.getId())
                .contentType(jsonType()).content(body(en(30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        Turno actualizado = turnos.findById(turno.getId()).orElseThrow();
        assertThat(actualizado.getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
        assertThat(actualizado.isRecordatorioEnviado()).isFalse();
        assertThat(actualizado.isAlertaPendienteEnviada()).isFalse();
        verify(notificaciones).reprogramacion(any(Turno.class));
    }

    @Test
    void reprogramarEnElBordeExactoDe24HorasQuedaPendiente() throws Exception {
        Turno turno = guardar(en(6), EstadoTurno.CONFIRMADO, "repro-borde24");

        autenticado(patch("/api/turnos/{id}/reprogramar", turno.getId())
                .contentType(jsonType()).content(body(en(24))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        Turno actualizado = turnos.findById(turno.getId()).orElseThrow();
        assertThat(actualizado.getInicio()).isEqualTo(en(24));
        assertThat(actualizado.isRecordatorioEnviado()).isFalse();
        assertThat(actualizado.isAlertaPendienteEnviada()).isFalse();
    }

    @Test
    void falloDelAvisoNoRevierteLaReprogramacion() throws Exception {
        Turno turno = guardar(en(30), EstadoTurno.PENDIENTE, "repro-fallo-aviso");
        when(notificaciones.reprogramacion(any(Turno.class)))
                .thenReturn(Boolean.FALSE);

        autenticado(patch("/api/turnos/{id}/reprogramar", turno.getId())
                .contentType(jsonType()).content(body(en(48))))
                .andExpect(status().isOk());

        assertThat(turnos.findById(turno.getId()).orElseThrow().getInicio()).isEqualTo(en(48));
        verify(notificaciones).reprogramacion(any(Turno.class));
    }

    private String body(java.time.Instant inicio) {
        return "{\"inicio\":\"" + local(inicio) + "\"}";
    }
}
