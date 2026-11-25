package ar.turnos.odontologia.turno;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/** GET /api/turnos y GET /api/turnos/{id}: agenda autenticada, filtros y validación de rango. */
class TurnoAgendaApiIT extends TurnosApiITBase {

    private static final Path EJEMPLOS = Path.of("..", "docs", "api", "ejemplos");

    @Test
    void rangoDevuelveTodosLosEstadosConLaFormaDelContratoYSinToken() throws Exception {
        guardar(en(25), EstadoTurno.PENDIENTE, "agenda-pendiente");
        guardar(en(26), EstadoTurno.CONFIRMADO, "agenda-confirmado");
        guardar(en(30), EstadoTurno.CANCELADO, "agenda-cancelado");

        String respuesta = autenticado(get("/api/turnos")
                .param("desde", "2026-12-02").param("hasta", "2026-12-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[1].estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$[2].estado").value("CANCELADO"))
                .andExpect(jsonPath("$[2].canceladoPor").value(nullValue()))
                .andExpect(jsonPath("$[0].telefonoContacto").value("+54 9 11 0000-0000"))
                .andExpect(jsonPath("$[0].pendienteMenos24h").value(false))
                .andExpect(jsonPath("$[0].token").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode esperado = json.readTree(Files.readString(EJEMPLOS.resolve("turnos-listar.200.json")));
        org.assertj.core.api.Assertions.assertThat(claves(json.readTree(respuesta).get(0)))
                .isEqualTo(claves(esperado.get(0)));
    }

    @Test
    void filtroPorEstadoDevuelveSoloLosConfirmados() throws Exception {
        guardar(en(25), EstadoTurno.PENDIENTE, "filtro-pendiente");
        guardar(en(26), EstadoTurno.CONFIRMADO, "filtro-confirmado");

        autenticado(get("/api/turnos")
                .param("desde", "2026-12-02").param("hasta", "2026-12-03")
                .param("estado", "CONFIRMADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("CONFIRMADO"));
    }

    @Test
    void detalleExistenteDevuelveLaFormaDelContratoYSinToken() throws Exception {
        Turno turno = guardar(en(25), EstadoTurno.CONFIRMADO, "detalle");

        autenticado(get("/api/turnos/{id}", turno.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(turno.getId()))
                .andExpect(jsonPath("$.emailContacto").value("paciente@ejemplo.com"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void detalleInexistenteEs404YSinJwtEs401() throws Exception {
        autenticado(get("/api/turnos/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));

        mvc.perform(get("/api/turnos").param("desde", "2026-12-02").param("hasta", "2026-12-03"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void rangoInvalidoEstadoInvalidoYParametrosAusentesSon400ConDetalles() throws Exception {
        autenticado(get("/api/turnos")
                .param("desde", "2026-12-01").param("hasta", "2027-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[?(@.campo == 'hasta')]").exists());

        autenticado(get("/api/turnos")
                .param("desde", "2026-12-03").param("hasta", "2026-12-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[?(@.campo == 'hasta')]").exists());

        autenticado(get("/api/turnos")
                .param("desde", "2026-12-02").param("hasta", "2026-12-03")
                .param("estado", "NO_EXISTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[?(@.campo == 'estado')]").exists());

        autenticado(get("/api/turnos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles.length()").value(2));
    }

    private static Set<String> claves(JsonNode nodo) {
        Set<String> claves = new TreeSet<>();
        for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
            claves.add(it.next());
        }
        return claves;
    }
}
