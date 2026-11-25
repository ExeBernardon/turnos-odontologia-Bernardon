package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.auth.JwtService;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.horario.HorarioAtencion;
import ar.turnos.odontologia.horario.HorarioAtencionRepository;
import ar.turnos.odontologia.notificacion.NotificacionTurnos;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.paciente.PacienteRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;

/** POST /api/turnos: alta manual autenticada con paciente existente o alta rapida. */
@IntegracionPostgres
@AutoConfigureMockMvc
@Import(TurnoManualApiIT.RelojFijo.class)
class TurnoManualApiIT {

    private static final Path EJEMPLOS = Path.of("..", "docs", "api", "ejemplos");
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final Instant AHORA = Instant.parse("2026-12-02T12:00:00Z");
    private static final ZoneOffset OFFSET = ZoneOffset.ofHours(-3);

    @TestConfiguration(proxyBeanMethods = false)
    static class RelojFijo {
        @Bean
        @Primary
        Clock clockFijo() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

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
    @MockitoBean
    private NotificacionTurnos notificaciones;

    private Paciente paciente;
    private Prestacion prestacion;

    @BeforeEach
    void preparar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre LIKE 'Carga manual%'");
        jdbc.update("DELETE FROM horario_atencion WHERE dia_semana = 6");
        paciente = pacientes.saveAndFlush(new Paciente("Ana", "Ejemplo", "99999999"));
        prestacion = prestaciones.saveAndFlush(new Prestacion("Carga manual", 30));
        horarios.saveAndFlush(new HorarioAtencion(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre LIKE 'Carga manual%'");
        jdbc.update("DELETE FROM horario_atencion WHERE dia_semana = 6");
    }

    @Test
    void pacienteExistenteDevuelve201ConContratoYSinToken() throws Exception {
        String cuerpo = mvc.perform(post("/api/turnos").header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).content(conPacienteExistente(proximoSabado(10, 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paciente.dni").value("99999999"))
                .andExpect(jsonPath("$.consentimientoAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(claves(json.readTree(cuerpo)))
                .isEqualTo(claves(json.readTree(Files.readString(EJEMPLOS.resolve("turnos-crear.201.json")))));
        Turno guardado = turnos.findAll().get(0);
        assertThat(guardado.getConsentimientoAt()).isNull();
        assertThat(guardado.getEmailContacto()).isEqualTo("paciente@ejemplo.com");
        assertThat(guardado.getTelefonoContacto()).isEqualTo("+54 9 11 0000-0000");
        assertThat(guardado.getToken()).isNotBlank();
    }

    @Test
    void altaRapidaCreaPacienteConDniNormalizado() throws Exception {
        String contenido = """
                {"paciente":{"nombre":"Nuevo","apellido":"Paciente","dni":"99.999.991"},
                 "prestacionId":%d,"inicio":"%s",
                 "emailContacto":"nuevo@ejemplo.com","telefonoContacto":"+54 9 11 0000-0000"}
                """.formatted(prestacion.getId(), proximoSabado(11, 0).format(FORMATO));

        mvc.perform(post("/api/turnos").header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paciente.dni").value("99999991"))
                .andExpect(jsonPath("$.paciente.nombre").value("Nuevo"));

        assertThat(pacientes.findByDni("99999991")).isPresent();
    }

    @Test
    void menosDeDosHorasEsAceptadoYConfirmado() throws Exception {
        OffsetDateTime inicio = AHORA.atOffset(OFFSET).plusMinutes(30);

        mvc.perform(post("/api/turnos").header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).content(conPacienteExistente(inicio)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));
    }

    @Test
    void dniDuplicadoEs422() throws Exception {
        String contenido = """
                {"paciente":{"nombre":"Otro","apellido":"Paciente","dni":"99.999.999"},
                 "prestacionId":%d,"inicio":"%s",
                 "emailContacto":"nuevo@ejemplo.com","telefonoContacto":"+54 9 11 0000-0000"}
                """.formatted(prestacion.getId(), proximoSabado(12, 0).format(FORMATO));

        mvc.perform(post("/api/turnos").header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DNI_DUPLICADO"));

        assertThat(turnos.count()).isZero();
    }

    @Test
    void pacienteIdYPacienteJuntosEs400() throws Exception {
        String contenido = """
                {"pacienteId":%d,
                 "paciente":{"nombre":"Otro","apellido":"Paciente","dni":"99999992"},
                 "prestacionId":%d,"inicio":"%s",
                 "emailContacto":"nuevo@ejemplo.com","telefonoContacto":"+54 9 11 0000-0000"}
                """.formatted(paciente.getId(), prestacion.getId(), proximoSabado(13, 0).format(FORMATO));

        mvc.perform(post("/api/turnos").header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void sinPacienteEs400() throws Exception {
        String contenido = """
                {"prestacionId":%d,"inicio":"%s",
                 "emailContacto":"nuevo@ejemplo.com","telefonoContacto":"+54 9 11 0000-0000"}
                """.formatted(prestacion.getId(), proximoSabado(14, 0).format(FORMATO));

        mvc.perform(post("/api/turnos").header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void inicioSinOffsetEs400ConDetalles() throws Exception {
        String inicio = proximoSabado(15, 0).format(FORMATO).replaceAll("[+-]\\d{2}:\\d{2}$", "");

        mvc.perform(post("/api/turnos").header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).content(conPacienteExistente(inicio)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[?(@.campo == 'inicio')]").exists());
    }

    @Test
    void sinTokenEs401() throws Exception {
        mvc.perform(post("/api/turnos").contentType(MediaType.APPLICATION_JSON)
                .content(conPacienteExistente(proximoSabado(16, 0))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void pacienteInexistenteEs404() throws Exception {
        String contenido = """
                {"pacienteId":999999,"prestacionId":%d,"inicio":"%s",
                 "emailContacto":"nuevo@ejemplo.com","telefonoContacto":"+54 9 11 0000-0000"}
                """.formatted(prestacion.getId(), proximoSabado(17, 0).format(FORMATO));

        mvc.perform(post("/api/turnos").header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    private String token() {
        return new JwtService(app, Clock.fixed(AHORA, ZoneOffset.UTC))
                .emitir(app.seed().odontologo().email()).token();
    }

    private String conPacienteExistente(OffsetDateTime inicio) {
        return conPacienteExistente(inicio.format(FORMATO));
    }

    private String conPacienteExistente(String inicio) {
        return """
                {"pacienteId":%d,"prestacionId":%d,"inicio":"%s",
                 "emailContacto":"paciente@ejemplo.com","telefonoContacto":"+54 9 11 0000-0000"}
                """.formatted(paciente.getId(), prestacion.getId(), inicio);
    }

    private OffsetDateTime proximoSabado(int hora, int minuto) {
        LocalDate dia = LocalDate.ofInstant(AHORA, app.zonaHoraria()).plusDays(1);
        while (dia.getDayOfWeek() != DayOfWeek.SATURDAY) {
            dia = dia.plusDays(1);
        }
        return dia.atTime(hora, minuto).atZone(app.zonaHoraria()).toOffsetDateTime();
    }

    private static Set<String> claves(JsonNode nodo) {
        Set<String> claves = new TreeSet<>();
        for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
            claves.add(it.next());
        }
        return claves;
    }
}
