package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.horario.HorarioAtencion;
import ar.turnos.odontologia.horario.HorarioAtencionRepository;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.paciente.PacienteRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;

/** GET /api/public/turnos/{token}: solo lectura y sin datos sensibles. */
@IntegracionPostgres
@AutoConfigureMockMvc
class TurnoTokenApiIT {

    private static final Path EJEMPLOS = Path.of("..", "docs", "api", "ejemplos");
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AppProperties app;
    @Autowired
    private TurnoRepository turnos;
    @Autowired
    private PacienteRepository pacientes;
    @Autowired
    private PrestacionRepository prestaciones;
    @Autowired
    private HorarioAtencionRepository horarios;

    private Paciente paciente;
    private Prestacion prestacion;
    private int secuencia;

    @BeforeEach
    void preparar() {
        paciente = pacientes.saveAndFlush(new Paciente("Ana", "Ejemplo", "99999993"));
        prestacion = prestaciones.saveAndFlush(new Prestacion("Consulta comun", 30));
        horarios.saveAndFlush(new HorarioAtencion(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre = 'Consulta comun'");
        jdbc.update("DELETE FROM horario_atencion WHERE dia_semana = 6");
    }

    @Test
    void consultaPorTokenDevuelveSoloLosCamposDelContrato() throws Exception {
        String token = reservarPorApi();

        String cuerpo = mvc.perform(get("/api/public/turnos/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombrePaciente").value("Ana"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(claves(json.readTree(cuerpo)))
                .isEqualTo(claves(json.readTree(Files.readString(EJEMPLOS.resolve("turnos-publico-reservar.201.json")))));
        assertThat(cuerpo).doesNotContain("dni", "apellido", "email", "telefono", "\"id\"", "token");
    }

    @Test
    void consultasRepetidasNoCambianElEstado() throws Exception {
        String token = reservarPorApi();

        String primera = mvc.perform(get("/api/public/turnos/{token}", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String segunda = mvc.perform(get("/api/public/turnos/{token}", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(segunda).isEqualTo(primera);
        assertThat(turnos.findByToken(token).orElseThrow().getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
    }

    @Test
    void tokenInexistenteEs404SinRevelarDatos() throws Exception {
        String cuerpo = mvc.perform(get("/api/public/turnos/{token}", "tokenInexistenteDePrueba123456789012345"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(cuerpo).get("codigo").asText()).isEqualTo("TOKEN_INVALIDO");
        assertThat(cuerpo).doesNotContain("Ana", "Ejemplo", "99999993");
    }

    @Test
    void permisosDelResumenDependenDelEstadoYElPlazo() throws Exception {
        Instant ahora = Instant.now();
        String lejano = crearTurno(EstadoTurno.PENDIENTE, ahora.plus(Duration.ofDays(3)));
        String proximo = crearTurno(EstadoTurno.PENDIENTE, ahora.plus(Duration.ofHours(12)));
        String confirmado = crearTurno(EstadoTurno.CONFIRMADO, ahora.plus(Duration.ofHours(36)));

        mvc.perform(get("/api/public/turnos/{token}", lejano))
                .andExpect(jsonPath("$.puedeConfirmar").value(false))
                .andExpect(jsonPath("$.puedeCancelar").value(true));
        mvc.perform(get("/api/public/turnos/{token}", proximo))
                .andExpect(jsonPath("$.puedeConfirmar").value(true))
                .andExpect(jsonPath("$.puedeCancelar").value(false));
        mvc.perform(get("/api/public/turnos/{token}", confirmado))
                .andExpect(jsonPath("$.puedeConfirmar").value(false))
                .andExpect(jsonPath("$.puedeCancelar").value(false));
    }

    private String reservarPorApi() throws Exception {
        LocalDate dia = LocalDate.now(app.zonaHoraria()).plusDays(1);
        while (dia.getDayOfWeek() != DayOfWeek.SATURDAY) {
            dia = dia.plusDays(1);
        }
        OffsetDateTime inicio = dia.atTime(10, 0).atZone(app.zonaHoraria()).toOffsetDateTime();
        mvc.perform(post("/api/public/turnos").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Ana","apellido":"Ejemplo","dni":"99999993",
                         "email":"ana@ejemplo.com","telefono":"+54 9 11 0000-0000",
                         "prestacionId":%d,"inicio":"%s","consentimiento":true}
                        """.formatted(prestacion.getId(), inicio.format(FORMATO))))
                .andExpect(status().isCreated());
        return turnos.findAll().get(0).getToken();
    }

    private String crearTurno(EstadoTurno estado, Instant inicio) {
        String token = "tokenDePruebaUnicoParaResumen" + (++secuencia) + "1234567890";
        turnos.saveAndFlush(new Turno(paciente, prestacion, inicio, inicio.plus(Duration.ofMinutes(30)),
                estado, token, "ana@ejemplo.com", "11 5555-5555"));
        return token;
    }

    private static Set<String> claves(JsonNode nodo) {
        Set<String> claves = new TreeSet<>();
        for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
            claves.add(it.next());
        }
        return claves;
    }
}
