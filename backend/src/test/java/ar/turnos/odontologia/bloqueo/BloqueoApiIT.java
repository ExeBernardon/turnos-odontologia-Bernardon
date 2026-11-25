package ar.turnos.odontologia.bloqueo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.notificacion.NotificacionTurnos;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.paciente.PacienteRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;
import ar.turnos.odontologia.turno.EstadoTurno;
import ar.turnos.odontologia.turno.Turno;
import ar.turnos.odontologia.turno.TurnoRepository;

@IntegracionPostgres
@AutoConfigureMockMvc
class BloqueoApiIT {

    private static final Path EJEMPLOS = Path.of("..", "docs", "api", "ejemplos");
    private static final String PREFIJO = "T10 ";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private BloqueoRepository bloqueos;
    @Autowired
    private PacienteRepository pacientes;
    @Autowired
    private PrestacionRepository prestaciones;
    @MockitoSpyBean
    private TurnoRepository turnos;
    @MockitoBean
    private NotificacionTurnos notificaciones;

    private int secuencia;
    private Paciente paciente;
    private Prestacion prestacion;

    @BeforeEach
    void preparar() {
        limpiar();
        paciente = pacientes.saveAndFlush(new Paciente("Ana", "Ejemplo", "99999999"));
        prestacion = prestaciones.saveAndFlush(new Prestacion(PREFIJO + "Consulta", 30));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE bloqueo, turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre LIKE ?", PREFIJO + "%");
    }

    // ---- utilidades ----

    private static Instant t(String hhmm) {
        return OffsetDateTime.parse("2026-12-24T" + hhmm + ":00-03:00").toInstant();
    }

    private Turno turno(String desde, String hasta, EstadoTurno estado) {
        Turno t = new Turno(paciente, prestacion, t(desde), t(hasta), estado, "tok-" + (++secuencia),
                "paciente@ejemplo.com", "+54 9 11 0000-0000");
        return turnos.saveAndFlush(t);
    }

    private static String cuerpo(String inicio, String fin, String motivo, Boolean cancelar) {
        StringBuilder sb = new StringBuilder("{\"inicio\":\"2026-12-24T" + inicio + ":00-03:00\",\"fin\":\"2026-12-24T"
                + fin + ":00-03:00\"");
        if (motivo != null) {
            sb.append(",\"motivo\":\"").append(motivo).append('"');
        }
        if (cancelar != null) {
            sb.append(",\"cancelarTurnosAfectados\":").append(cancelar);
        }
        return sb.append('}').toString();
    }

    private Set<String> clavesDelEjemplo(String archivo) throws Exception {
        return claves(json.readTree(Files.readString(EJEMPLOS.resolve(archivo))));
    }

    private JsonNode ejemplo(String archivo) throws Exception {
        return json.readTree(Files.readString(EJEMPLOS.resolve(archivo)));
    }

    private static List<Long> ids(JsonNode arreglo) {
        List<Long> ids = new java.util.ArrayList<>();
        arreglo.forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    private static Set<String> claves(JsonNode n) {
        Set<String> s = new TreeSet<>();
        for (Iterator<String> it = n.fieldNames(); it.hasNext();) {
            s.add(it.next());
        }
        return s;
    }

    private JsonNode llamar(org.springframework.test.web.servlet.RequestBuilder rb, int esperado) throws Exception {
        String r = mvc.perform(rb).andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
        return r.isBlank() ? null : json.readTree(r);
    }

    private long cantidadBloqueos() {
        return jdbc.queryForObject("SELECT count(*) FROM bloqueo", Long.class);
    }

    private String estadoDe(Turno t) {
        return jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, t.getId());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(String url,
            String body) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ---- seguridad ----

    @Test
    void privadosSinAutenticacionDan401() throws Exception {
        mvc.perform(get("/api/bloqueos?desde=2026-12-01&hasta=2026-12-31")).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/bloqueos", cuerpo("09:00", "13:00", null, null)))
                .andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/bloqueos/previsualizar", cuerpo("09:00", "13:00", null, null)))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/bloqueos/1")).andExpect(status().isUnauthorized());
    }

    // ---- GET ----

    @Test
    @WithMockUser
    void listadoIncluyeLosQueSeSuperponenConElRangoYTieneLaFormaDelContrato() throws Exception {
        bloqueos.saveAndFlush(new Bloqueo(t("09:00"), t("13:00"), "Capacitación"));
        bloqueos.saveAndFlush(new Bloqueo(Instant.parse("2027-02-01T10:00:00Z"), Instant.parse("2027-02-01T11:00:00Z"),
                "Fuera"));

        JsonNode r = llamar(get("/api/bloqueos?desde=2026-12-20&hasta=2026-12-27"), 200);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).get("motivo").asText()).isEqualTo("Capacitación");
        assertThat(r.get(0).get("inicio").asText()).isEqualTo("2026-12-24T09:00:00-03:00");
        assertThat(claves(r.get(0))).isEqualTo(claves(ejemplo("bloqueos-listar.200.json").get(0)));
    }

    @Test
    @WithMockUser
    void listadoSinCoincidenciasEsListaVacia() throws Exception {
        bloqueos.saveAndFlush(new Bloqueo(t("09:00"), t("13:00"), null));
        assertThat(llamar(get("/api/bloqueos?desde=2026-12-01&hasta=2026-12-23"), 200)).isEmpty();
    }

    @Test
    @WithMockUser
    void listadoUsaDiasCalendarioDeBuenosAires() throws Exception {
        // 23:30 del 23 a 00:30 del 24 (-03:00): el dia 24 lo ve, el dia 25 no
        bloqueos.saveAndFlush(new Bloqueo(OffsetDateTime.parse("2026-12-23T23:30:00-03:00").toInstant(),
                OffsetDateTime.parse("2026-12-24T00:30:00-03:00").toInstant(), "Noche"));
        assertThat(llamar(get("/api/bloqueos?desde=2026-12-24&hasta=2026-12-24"), 200)).hasSize(1);
        assertThat(llamar(get("/api/bloqueos?desde=2026-12-25&hasta=2026-12-25"), 200)).isEmpty();
    }

    @Test
    @WithMockUser
    void listadoConRangoInvalidoOSinParametrosDa400Validacion() throws Exception {
        for (String q : new String[] { "desde=2026-12-10&hasta=2026-12-09", "desde=2026-12-01&hasta=2027-01-01",
                "hasta=2026-12-09", "desde=2026-12-09" }) {
            JsonNode r = llamar(get("/api/bloqueos?" + q), 400);
            assertThat(r.get("codigo").asText()).as(q).isEqualTo("VALIDACION");
        }
    }

    @Test
    @WithMockUser
    void listadoAceptaExactamente31Dias() throws Exception {
        llamar(get("/api/bloqueos?desde=2026-12-01&hasta=2026-12-31"), 200);
    }

    // ---- DELETE ----

    @Test
    @WithMockUser
    void bajaDa204YElBloqueoDeJaDeListarse() throws Exception {
        Bloqueo b = bloqueos.saveAndFlush(new Bloqueo(t("09:00"), t("13:00"), null));
        mvc.perform(delete("/api/bloqueos/" + b.getId())).andExpect(status().isNoContent());
        assertThat(cantidadBloqueos()).isZero();
    }

    @Test
    @WithMockUser
    void bajaInexistenteDa404() throws Exception {
        mvc.perform(delete("/api/bloqueos/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    @WithMockUser
    void bajaNoRestituyeLosTurnosCancelados() throws Exception {
        turno("10:00", "10:30", EstadoTurno.PENDIENTE);
        JsonNode creado = llamar(postJson("/api/bloqueos", cuerpo("09:00", "13:00", null, true)), 201);
        long id = creado.get("bloqueo").get("id").asLong();

        mvc.perform(delete("/api/bloqueos/" + id)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT estado FROM turno", String.class)).isEqualTo("CANCELADO");
    }

    // ---- previsualizar ----

    @Test
    @WithMockUser
    void previsualizarListaSoloActivosQueInterseca() throws Exception {
        Turno a = turno("10:00", "10:30", EstadoTurno.PENDIENTE);
        Turno b = turno("11:00", "11:45", EstadoTurno.CONFIRMADO);
        turno("12:00", "12:30", EstadoTurno.CANCELADO);
        turno("13:00", "13:30", EstadoTurno.PENDIENTE); // toca el borde fin
        turno("08:30", "09:00", EstadoTurno.PENDIENTE); // toca el borde inicio
        turno("08:45", "09:15", EstadoTurno.AUSENTE); // no activo

        JsonNode r = llamar(postJson("/api/bloqueos/previsualizar", cuerpo("09:00", "13:00", "x", null)), 200);

        List<Long> ids = ids(r.get("turnosAfectados"));
        assertThat(ids).containsExactly(a.getId(), b.getId());
        JsonNode primero = r.get("turnosAfectados").get(0);
        assertThat(claves(r)).isEqualTo(clavesDelEjemplo("bloqueos-previsualizar.200.json"));
        JsonNode ej = ejemplo("bloqueos-previsualizar.200.json").get("turnosAfectados").get(0);
        assertThat(claves(primero)).isEqualTo(claves(ej));
        assertThat(claves(primero.get("paciente"))).isEqualTo(claves(ej.get("paciente")));
        assertThat(claves(primero.get("prestacion"))).isEqualTo(claves(ej.get("prestacion")));
        assertThat(primero.get("inicio").asText()).isEqualTo("2026-12-24T10:00:00-03:00");
        assertThat(primero.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(primero.get("canceladoPor").isNull()).isTrue();
        assertThat(primero.has("token")).isFalse();
    }

    @Test
    @WithMockUser
    void previsualizarNoModificaNada() throws Exception {
        Turno a = turno("10:00", "10:30", EstadoTurno.PENDIENTE);

        llamar(postJson("/api/bloqueos/previsualizar", cuerpo("09:00", "13:00", null, true)), 200);

        assertThat(cantidadBloqueos()).isZero();
        assertThat(estadoDe(a)).isEqualTo("PENDIENTE");
        verify(notificaciones, never()).cancelacionPorBloqueo(any());
    }

    @Test
    @WithMockUser
    void previsualizarSinTurnosDaListaVacia() throws Exception {
        JsonNode r = llamar(postJson("/api/bloqueos/previsualizar", cuerpo("09:00", "13:00", null, null)), 200);
        assertThat(r.get("turnosAfectados")).isEmpty();
    }

    @Test
    @WithMockUser
    void previsualizarConRangoInvalidoDa422() throws Exception {
        for (String[] r : new String[][] { { "13:00", "09:00" }, { "09:00", "09:00" } }) {
            JsonNode e = llamar(postJson("/api/bloqueos/previsualizar", cuerpo(r[0], r[1], null, null)), 422);
            assertThat(e.get("codigo").asText()).isEqualTo("RANGO_HORARIO_INVALIDO");
        }
    }

    @Test
    @WithMockUser
    void previsualizarSinInicioDa400Validacion() throws Exception {
        JsonNode e = llamar(postJson("/api/bloqueos/previsualizar", "{\"fin\":\"2026-12-24T13:00:00-03:00\"}"), 400);
        assertThat(e.get("codigo").asText()).isEqualTo("VALIDACION");
    }

    // ---- crear sin turnos afectados ----

    @Test
    @WithMockUser
    void crearSinTurnosAfectadosDa201YNoRequiereConfirmacion() throws Exception {
        JsonNode r = llamar(postJson("/api/bloqueos", cuerpo("09:00", "13:00", "Capacitación", null)), 201);

        assertThat(claves(r)).isEqualTo(clavesDelEjemplo("bloqueos-crear.201.json"));
        assertThat(claves(r.get("bloqueo"))).isEqualTo(claves(ejemplo("bloqueos-crear.201.json").get("bloqueo")));
        assertThat(r.get("bloqueo").get("motivo").asText()).isEqualTo("Capacitación");
        assertThat(r.get("bloqueo").get("inicio").asText()).isEqualTo("2026-12-24T09:00:00-03:00");
        assertThat(r.get("turnosCancelados")).isEmpty();
        assertThat(cantidadBloqueos()).isEqualTo(1);
    }

    @Test
    @WithMockUser
    void crearSinMotivoGuardaNulo() throws Exception {
        JsonNode r = llamar(postJson("/api/bloqueos", cuerpo("09:00", "13:00", null, false)), 201);
        assertThat(r.get("bloqueo").get("motivo").isNull()).isTrue();
    }

    @Test
    @WithMockUser
    void losBloqueosPuedenSolaparse() throws Exception {
        llamar(postJson("/api/bloqueos", cuerpo("09:00", "13:00", null, null)), 201);
        llamar(postJson("/api/bloqueos", cuerpo("11:00", "15:00", null, null)), 201);
        assertThat(cantidadBloqueos()).isEqualTo(2);
    }

    @Test
    @WithMockUser
    void crearConRangoInvalidoDa422YNoCreaNada() throws Exception {
        for (String[] r : new String[][] { { "13:00", "09:00" }, { "09:00", "09:00" } }) {
            JsonNode e = llamar(postJson("/api/bloqueos", cuerpo(r[0], r[1], null, true)), 422);
            assertThat(e.get("codigo").asText()).isEqualTo("RANGO_HORARIO_INVALIDO");
        }
        assertThat(cantidadBloqueos()).isZero();
    }

    @Test
    @WithMockUser
    void crearConCuerpoInvalidoDa400() throws Exception {
        JsonNode e = llamar(postJson("/api/bloqueos", cuerpo("09:00", "13:00", "x".repeat(201), null)), 400);
        assertThat(e.get("codigo").asText()).isEqualTo("VALIDACION");
        assertThat(e.get("detalles").get(0).get("campo").asText()).isEqualTo("motivo");
        assertThat(llamar(postJson("/api/bloqueos", "{\"inicio\":\"2026-12-24T09:00:00-03:00\"}"), 400)
                .get("codigo").asText()).isEqualTo("VALIDACION");
        assertThat(llamar(postJson("/api/bloqueos", "no es json"), 400).get("codigo").asText())
                .isEqualTo("SOLICITUD_MAL_FORMADA");
    }

    // ---- 409 sin confirmacion ----

    @Test
    @WithMockUser
    void conTurnosAfectadosYSinConfirmarDa409ConLaListaYNoCreaNada() throws Exception {
        Turno a = turno("10:00", "10:30", EstadoTurno.PENDIENTE);
        Turno b = turno("11:00", "11:45", EstadoTurno.CONFIRMADO);

        for (Boolean cancelar : new Boolean[] { false, null }) {
            JsonNode e = llamar(postJson("/api/bloqueos", cuerpo("09:00", "13:00", null, cancelar)), 409);

            assertThat(e.get("codigo").asText()).isEqualTo("BLOQUEO_CON_TURNOS_AFECTADOS");
            assertThat(e.get("status").asInt()).isEqualTo(409);
            assertThat(e.get("path").asText()).isEqualTo("/api/bloqueos");
            assertThat(claves(e)).isEqualTo(clavesDelEjemplo("bloqueos-crear.409-bloqueo-con-turnos-afectados.json"));
            assertThat(ids(e.get("turnosAfectados"))).containsExactly(a.getId(), b.getId());
            assertThat(claves(e.get("turnosAfectados").get(0))).isEqualTo(claves(
                    ejemplo("bloqueos-crear.409-bloqueo-con-turnos-afectados.json").get("turnosAfectados").get(0)));
        }
        assertThat(cantidadBloqueos()).isZero();
        assertThat(estadoDe(a)).isEqualTo("PENDIENTE");
        assertThat(estadoDe(b)).isEqualTo("CONFIRMADO");
        verify(notificaciones, never()).cancelacionPorBloqueo(any());
    }

    // ---- cancelacion atomica ----

    @Test
    @WithMockUser
    void confirmadoCreaElBloqueoYCancelaLosTurnosAfectados() throws Exception {
        Turno a = turno("10:00", "10:30", EstadoTurno.PENDIENTE);
        Turno b = turno("11:00", "11:45", EstadoTurno.CONFIRMADO);
        Turno fuera = turno("14:00", "14:30", EstadoTurno.PENDIENTE);

        JsonNode r = llamar(postJson("/api/bloqueos", cuerpo("09:00", "13:00", "Capacitación", true)), 201);

        assertThat(cantidadBloqueos()).isEqualTo(1);
        assertThat(estadoDe(a)).isEqualTo("CANCELADO");
        assertThat(estadoDe(b)).isEqualTo("CANCELADO");
        assertThat(estadoDe(fuera)).isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForList("SELECT cancelado_por FROM turno WHERE estado = 'CANCELADO'", String.class))
                .containsOnly("ODONTOLOGO");
        JsonNode cancelados = r.get("turnosCancelados");
        assertThat(ids(cancelados)).containsExactly(a.getId(), b.getId());
        assertThat(cancelados.get(0).get("estado").asText()).isEqualTo("CANCELADO");
        assertThat(cancelados.get(0).get("canceladoPor").asText()).isEqualTo("ODONTOLOGO");
        assertThat(claves(cancelados.get(0))).isEqualTo(
                claves(ejemplo("bloqueos-crear.201.json").get("turnosCancelados").get(0)));
    }

    @Test
    @WithMockUser
    void siFallaLaCancelacionNoQuedaNiElBloqueoNiNingunaCancelacion() throws Exception {
        Turno a = turno("10:00", "10:30", EstadoTurno.PENDIENTE);
        doThrow(new IllegalStateException("falla simulada")).when(turnos).saveAllAndFlush(any());

        JsonNode e = llamar(postJson("/api/bloqueos", cuerpo("09:00", "13:00", null, true)), 500);

        assertThat(e.get("codigo").asText()).isEqualTo("ERROR_INTERNO");
        assertThat(cantidadBloqueos()).isZero();
        assertThat(estadoDe(a)).isEqualTo("PENDIENTE");
        verify(notificaciones, never()).cancelacionPorBloqueo(any());
    }

    @Test
    @WithMockUser
    void losTurnosCanceladosLiberanElHorario() throws Exception {
        turno("10:00", "10:30", EstadoTurno.PENDIENTE);
        llamar(postJson("/api/bloqueos", cuerpo("09:00", "13:00", null, true)), 201);

        Turno nuevo = turno("10:00", "10:30", EstadoTurno.PENDIENTE);

        assertThat(nuevo.getId()).isNotNull();
    }

    @Test
    @WithMockUser
    void confirmarSinTurnosAfectadosCreaDirectoYNoAvisaANadie() throws Exception {
        JsonNode r = llamar(postJson("/api/bloqueos", cuerpo("09:00", "13:00", null, true)), 201);
        assertThat(r.get("turnosCancelados")).isEmpty();
        verify(notificaciones, never()).cancelacionPorBloqueo(any());
    }

    // ---- avisos ----

    @Test
    @WithMockUser
    void avisaUnaVezPorTurnoYDespuesDelCommit() throws Exception {
        Turno a = turno("10:00", "10:30", EstadoTurno.PENDIENTE);
        Turno b = turno("11:00", "11:45", EstadoTurno.CONFIRMADO);
        AtomicBoolean confirmadoAlAvisar = new AtomicBoolean(true);
        doAnswer(inv -> {
            // conexion nueva, fuera de cualquier transaccion: solo ve lo ya confirmado
            try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
                    ResultSet rs = s.executeQuery("SELECT (SELECT count(*) FROM bloqueo), "
                            + "(SELECT count(*) FROM turno WHERE estado = 'CANCELADO')")) {
                rs.next();
                confirmadoAlAvisar.compareAndSet(true, rs.getLong(1) == 1 && rs.getLong(2) == 2);
            }
            Turno aviso = inv.getArgument(0);
            // el puerto debe poder leer las asociaciones LAZY
            assertThat(aviso.getPaciente().getNombre()).isEqualTo("Ana");
            assertThat(aviso.getPrestacion().getNombre()).isEqualTo(PREFIJO + "Consulta");
            return true;
        }).when(notificaciones).cancelacionPorBloqueo(any());

        llamar(postJson("/api/bloqueos", cuerpo("09:00", "13:00", null, true)), 201);

        verify(notificaciones, times(2)).cancelacionPorBloqueo(any());
        assertThat(confirmadoAlAvisar).isTrue();
        assertThat(a.getId()).isNotEqualTo(b.getId());
    }

    @Test
    @WithMockUser
    void unAvisoQueLanzaNoRevierteNadaNiImpideLosDemas() throws Exception {
        turno("10:00", "10:30", EstadoTurno.PENDIENTE);
        turno("11:00", "11:45", EstadoTurno.CONFIRMADO);
        when(notificaciones.cancelacionPorBloqueo(any())).thenThrow(new IllegalStateException("SMTP caido"));

        JsonNode r = llamar(postJson("/api/bloqueos", cuerpo("09:00", "13:00", null, true)), 201);

        assertThat(r.get("turnosCancelados")).hasSize(2);
        assertThat(cantidadBloqueos()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT estado FROM turno", String.class)).containsOnly("CANCELADO");
        verify(notificaciones, times(2)).cancelacionPorBloqueo(any());
    }

    @Test
    @WithMockUser
    void unAvisoQueDevuelveFalseTampocoRevierteNada() throws Exception {
        turno("10:00", "10:30", EstadoTurno.PENDIENTE);
        when(notificaciones.cancelacionPorBloqueo(any())).thenReturn(false);

        llamar(postJson("/api/bloqueos", cuerpo("09:00", "13:00", null, true)), 201);

        assertThat(cantidadBloqueos()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado FROM turno", String.class)).isEqualTo("CANCELADO");
    }
}
