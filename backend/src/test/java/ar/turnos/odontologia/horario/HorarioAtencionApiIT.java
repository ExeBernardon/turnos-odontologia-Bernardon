package ar.turnos.odontologia.horario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
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
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.turnos.odontologia.IntegracionPostgres;

/**
 * Los tests usan sabado (6) y domingo (7): el seed solo carga lunes a viernes y otros tests
 * (DataSeederIT) cuentan esos diez rangos, asi que aca no se tocan.
 */
@IntegracionPostgres
@AutoConfigureMockMvc
class HorarioAtencionApiIT {

    private static final Path EJEMPLOS = Path.of("..", "docs", "api", "ejemplos");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private HorarioAtencionRepository horarios;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM horario_atencion WHERE dia_semana IN (6, 7)");
    }

    private static String body(int dia, String ini, String fin) {
        return "{\"diaSemana\":" + dia + (ini == null ? "" : ",\"horaInicio\":\"" + ini + "\"")
                + (fin == null ? "" : ",\"horaFin\":\"" + fin + "\"") + "}";
    }

    private ResultActions crear(int dia, String ini, String fin) throws Exception {
        return mvc.perform(post("/api/horarios-atencion").contentType(MediaType.APPLICATION_JSON)
                .content(body(dia, ini, fin)));
    }

    private long idDe(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static Set<String> claves(JsonNode n) {
        Set<String> s = new TreeSet<>();
        for (Iterator<String> it = n.fieldNames(); it.hasNext();) {
            s.add(it.next());
        }
        return s;
    }

    private Set<String> clavesDelEjemplo(String archivo) throws Exception {
        JsonNode n = json.readTree(Files.readString(EJEMPLOS.resolve(archivo)));
        return claves(n.isArray() ? n.get(0) : n);
    }

    @Test
    void privadosSinAutenticacionDan401() throws Exception {
        mvc.perform(get("/api/horarios-atencion")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/horarios-atencion").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/horarios-atencion/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/horarios-atencion/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void altaDevuelve201ConLaFormaYElFormatoHoraDelContrato() throws Exception {
        String r = crear(6, "09:00", "13:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.diaSemana").value(6))
                .andExpect(jsonPath("$.horaInicio").value("09:00"))
                .andExpect(jsonPath("$.horaFin").value("13:00"))
                .andReturn().getResponse().getContentAsString();

        assertThat(claves(json.readTree(r))).isEqualTo(clavesDelEjemplo("horarios-crear.201.json"));
        assertThat(horarios.findByDiaSemanaOrderByHoraInicio(DayOfWeek.SATURDAY)).hasSize(1);
    }

    @Test
    @WithMockUser
    void variosRangosEnElMismoDiaYAdyacentesSePermiten() throws Exception {
        crear(6, "09:00", "13:00").andExpect(status().isCreated());
        crear(6, "15:00", "19:00").andExpect(status().isCreated());
        crear(6, "13:00", "15:00").andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    void mismasHorasEnOtroDiaSePermiten() throws Exception {
        crear(6, "09:00", "13:00").andExpect(status().isCreated());
        crear(7, "09:00", "13:00").andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    void inicioIgualOPosteriorAFinDa422RangoInvalido() throws Exception {
        crear(6, "09:00", "09:00").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RANGO_HORARIO_INVALIDO"))
                .andExpect(jsonPath("$.path").value("/api/horarios-atencion"));
        crear(6, "14:00", "09:00").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RANGO_HORARIO_INVALIDO"));
        assertThat(horarios.findByDiaSemanaOrderByHoraInicio(DayOfWeek.SATURDAY)).isEmpty();
    }

    @Test
    @WithMockUser
    void rangoSolapadoDa422HorariosSolapados() throws Exception {
        crear(6, "09:00", "13:00").andExpect(status().isCreated());

        crear(6, "12:00", "15:00").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("HORARIOS_SOLAPADOS"));
        crear(6, "10:00", "11:00").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("HORARIOS_SOLAPADOS"));
        crear(6, "08:00", "09:30").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("HORARIOS_SOLAPADOS"));
        crear(6, "08:00", "14:00").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("HORARIOS_SOLAPADOS"));
        assertThat(horarios.findByDiaSemanaOrderByHoraInicio(DayOfWeek.SATURDAY)).hasSize(1);
    }

    @Test
    @WithMockUser
    void validacionDeCampos() throws Exception {
        crear(8, "09:00", "13:00").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[0].campo").value("diaSemana"));
        crear(0, "09:00", "13:00").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0].campo").value("diaSemana"));
        crear(6, "09:00", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0].campo").value("horaFin"));
        crear(6, null, "13:00").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0].campo").value("horaInicio"));
    }

    @Test
    @WithMockUser
    void horaIlegibleDa400SolicitudMalFormada() throws Exception {
        crear(6, "nueve", "13:00").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_MAL_FORMADA"));
    }

    @Test
    @WithMockUser
    void listadoOrdenadoPorDiaYHoraConLaFormaDelContrato() throws Exception {
        crear(7, "09:00", "13:00");
        crear(6, "15:00", "19:00");
        crear(6, "09:00", "13:00");

        String r = mvc.perform(get("/api/horarios-atencion"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode lista = json.readTree(r);
        assertThat(lista.size()).isEqualTo(13);
        assertThat(lista.get(10).get("diaSemana").asInt()).isEqualTo(6);
        assertThat(lista.get(10).get("horaInicio").asText()).isEqualTo("09:00");
        assertThat(lista.get(11).get("horaInicio").asText()).isEqualTo("15:00");
        assertThat(lista.get(12).get("diaSemana").asInt()).isEqualTo(7);
        assertThat(claves(lista.get(0))).isEqualTo(clavesDelEjemplo("horarios-listar.200.json"));
    }

    @Test
    @WithMockUser
    void putReemplazaYElPropioRangoNoCuentaComoSolapamiento() throws Exception {
        long id = idDe(crear(6, "09:00", "13:00"));

        String r = mvc.perform(put("/api/horarios-atencion/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body(6, "09:00", "13:00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(claves(json.readTree(r))).isEqualTo(clavesDelEjemplo("horarios-actualizar.200.json"));

        mvc.perform(put("/api/horarios-atencion/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body(7, "10:00", "14:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diaSemana").value(7))
                .andExpect(jsonPath("$.horaInicio").value("10:00"));
    }

    @Test
    @WithMockUser
    void putConRangoInvalidoOSolapadoDa422() throws Exception {
        long id = idDe(crear(6, "09:00", "13:00"));
        crear(6, "15:00", "19:00").andExpect(status().isCreated());

        mvc.perform(put("/api/horarios-atencion/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body(6, "14:00", "09:00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RANGO_HORARIO_INVALIDO"))
                .andExpect(jsonPath("$.path").value("/api/horarios-atencion/" + id));
        mvc.perform(put("/api/horarios-atencion/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body(6, "09:00", "16:00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("HORARIOS_SOLAPADOS"));

        HorarioAtencion sinCambios = horarios.findById(id).orElseThrow();
        assertThat(sinCambios.getHoraFin()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    @WithMockUser
    void putYDeleteInexistentesDan404() throws Exception {
        mvc.perform(put("/api/horarios-atencion/999999").contentType(MediaType.APPLICATION_JSON)
                .content(body(6, "09:00", "13:00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        mvc.perform(delete("/api/horarios-atencion/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    @WithMockUser
    void deleteDa204YLiberaElRango() throws Exception {
        long id = idDe(crear(6, "09:00", "13:00"));

        mvc.perform(delete("/api/horarios-atencion/" + id)).andExpect(status().isNoContent());

        assertThat(horarios.existsById(id)).isFalse();
        crear(6, "09:00", "13:00").andExpect(status().isCreated());
    }
}
