package ar.turnos.odontologia.prestacion;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.paciente.PacienteRepository;
import ar.turnos.odontologia.turno.EstadoTurno;
import ar.turnos.odontologia.turno.Turno;
import ar.turnos.odontologia.turno.TurnoRepository;

@IntegracionPostgres
@AutoConfigureMockMvc
class PrestacionApiIT {

    private static final Path EJEMPLOS = Path.of("..", "docs", "api", "ejemplos");
    private static final String PREFIJO = "T05 ";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PrestacionRepository prestaciones;
    @Autowired
    private PacienteRepository pacientes;
    @Autowired
    private TurnoRepository turnos;

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre LIKE ?", PREFIJO + "%");
    }

    private static String body(String nombre, Object duracion, Boolean activa) {
        return "{\"nombre\":\"" + nombre + "\",\"duracionMinutos\":" + duracion
                + (activa == null ? "" : ",\"activa\":" + activa) + "}";
    }

    private Set<String> clavesDelEjemplo(String archivo) throws Exception {
        JsonNode n = json.readTree(Files.readString(EJEMPLOS.resolve(archivo)));
        if (n.isArray()) {
            n = n.get(0);
        }
        return claves(n);
    }

    private static Set<String> claves(JsonNode n) {
        Set<String> s = new TreeSet<>();
        for (Iterator<String> it = n.fieldNames(); it.hasNext();) {
            s.add(it.next());
        }
        return s;
    }

    private Prestacion guardar(String nombre, int min, boolean activa) {
        Prestacion p = new Prestacion(PREFIJO + nombre, min);
        p.setActiva(activa);
        return prestaciones.saveAndFlush(p);
    }

    // ---- seguridad ----

    @Test
    void privadosSinAutenticacionDan401() throws Exception {
        mvc.perform(get("/api/prestaciones")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/prestaciones").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/prestaciones/1")).andExpect(status().isUnauthorized());
    }

    // ---- alta y listado ----

    @Test
    @WithMockUser
    void altaDevuelve201ConLaFormaDelContrato() throws Exception {
        String respuesta = mvc.perform(post("/api/prestaciones").contentType(MediaType.APPLICATION_JSON)
                .content(body(PREFIJO + "Consulta", 30, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value(PREFIJO + "Consulta"))
                .andExpect(jsonPath("$.duracionMinutos").value(30))
                .andExpect(jsonPath("$.activa").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(claves(json.readTree(respuesta))).isEqualTo(clavesDelEjemplo("prestaciones-crear.201.json"));
    }

    @Test
    @WithMockUser
    void altaConActivaFalsaLaRespeta() throws Exception {
        mvc.perform(post("/api/prestaciones").contentType(MediaType.APPLICATION_JSON)
                .content(body(PREFIJO + "Inactiva", 20, false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activa").value(false));
    }

    @Test
    @WithMockUser
    void altaConDuracionInvalidaDa400ConDetalleDelCampo() throws Exception {
        for (int d : new int[] { 0, -5, 481 }) {
            mvc.perform(post("/api/prestaciones").contentType(MediaType.APPLICATION_JSON)
                    .content(body(PREFIJO + "X", d, null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.detalles[0].campo").value("duracionMinutos"));
        }
    }

    @Test
    @WithMockUser
    void altaConNombreEnBlancoODuracionFaltanteDa400() throws Exception {
        mvc.perform(post("/api/prestaciones").contentType(MediaType.APPLICATION_JSON)
                .content(body("", 30, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0].campo").value("nombre"));
        mvc.perform(post("/api/prestaciones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"" + PREFIJO + "Y\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0].campo").value("duracionMinutos"));
    }

    @Test
    @WithMockUser
    void listadoIncluyeActivasEInactivasConLaFormaDelContrato() throws Exception {
        guardar("Activa", 30, true);
        guardar("Inactiva", 40, false);

        String respuesta = mvc.perform(get("/api/prestaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", hasItem(PREFIJO + "Activa")))
                .andExpect(jsonPath("$[*].nombre", hasItem(PREFIJO + "Inactiva")))
                .andReturn().getResponse().getContentAsString();

        assertThat(claves(json.readTree(respuesta).get(0))).isEqualTo(clavesDelEjemplo("prestaciones-listar.200.json"));
    }

    // ---- actualizacion ----

    @Test
    @WithMockUser
    void putReemplazaYDevuelve200() throws Exception {
        Prestacion p = guardar("Original", 30, true);

        String respuesta = mvc.perform(put("/api/prestaciones/" + p.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(body(PREFIJO + "Cambiada", 50, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(p.getId()))
                .andExpect(jsonPath("$.nombre").value(PREFIJO + "Cambiada"))
                .andExpect(jsonPath("$.duracionMinutos").value(50))
                .andExpect(jsonPath("$.activa").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(claves(json.readTree(respuesta))).isEqualTo(clavesDelEjemplo("prestaciones-actualizar.200.json"));
    }

    @Test
    @WithMockUser
    void putInexistenteDa404() throws Exception {
        mvc.perform(put("/api/prestaciones/999999").contentType(MediaType.APPLICATION_JSON)
                .content(body(PREFIJO + "Z", 30, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    @WithMockUser
    void putConDuracionInvalidaDa400() throws Exception {
        Prestacion p = guardar("Valida", 30, true);
        mvc.perform(put("/api/prestaciones/" + p.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(body(PREFIJO + "Valida", 0, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    @WithMockUser
    void cambiarLaDuracionNoModificaElFinDeTurnosExistentes() throws Exception {
        Prestacion p = guardar("ConTurno", 30, true);
        Paciente pac = pacientes.saveAndFlush(new Paciente("Ana", "Prueba", "99999999"));
        Instant inicio = Instant.parse("2026-12-01T13:00:00Z");
        Instant fin = inicio.plusSeconds(30 * 60);
        Turno t = turnos.saveAndFlush(new Turno(pac, p, inicio, fin, EstadoTurno.PENDIENTE, "tok-c05-dur",
                "paciente@ejemplo.com", "11 5555-5555"));

        mvc.perform(put("/api/prestaciones/" + p.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(body(PREFIJO + "ConTurno", 90, true)))
                .andExpect(status().isOk());

        assertThat(turnos.findById(t.getId()).orElseThrow().getFin()).isEqualTo(fin);
    }

    // ---- eliminacion ----

    @Test
    @WithMockUser
    void deleteSinTurnosDa204YLaElimina() throws Exception {
        Prestacion p = guardar("Borrable", 30, true);

        mvc.perform(delete("/api/prestaciones/" + p.getId())).andExpect(status().isNoContent());

        assertThat(prestaciones.existsById(p.getId())).isFalse();
    }

    @Test
    @WithMockUser
    void deleteConTurnosDa422PrestacionConTurnosYNoLaElimina() throws Exception {
        Prestacion p = guardar("Usada", 30, true);
        Paciente pac = pacientes.saveAndFlush(new Paciente("Ana", "Prueba", "99999999"));
        Instant inicio = Instant.parse("2026-12-01T14:00:00Z");
        turnos.saveAndFlush(new Turno(pac, p, inicio, inicio.plusSeconds(1800), EstadoTurno.CANCELADO, "tok-c05-del",
                "paciente@ejemplo.com", "11 5555-5555"));

        mvc.perform(delete("/api/prestaciones/" + p.getId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PRESTACION_CON_TURNOS"))
                .andExpect(jsonPath("$.path").value("/api/prestaciones/" + p.getId()));

        assertThat(prestaciones.existsById(p.getId())).isTrue();
    }

    @Test
    @WithMockUser
    void deleteInexistenteDa404() throws Exception {
        mvc.perform(delete("/api/prestaciones/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    // ---- publico ----

    @Test
    void publicoSinAutenticacionDevuelveSoloActivasSinElCampoActiva() throws Exception {
        guardar("PubActiva", 30, true);
        guardar("PubInactiva", 30, false);

        String respuesta = mvc.perform(get("/api/public/prestaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", hasItem(PREFIJO + "PubActiva")))
                .andExpect(jsonPath("$[*].nombre", not(hasItem(PREFIJO + "PubInactiva"))))
                .andReturn().getResponse().getContentAsString();

        JsonNode lista = json.readTree(respuesta);
        for (JsonNode n : lista) {
            assertThat(claves(n)).isEqualTo(clavesDelEjemplo("prestaciones-publicas-listar.200.json"));
        }
    }
}
