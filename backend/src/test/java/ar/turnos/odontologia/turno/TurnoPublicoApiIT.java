package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
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

/** Contrato de POST /api/public/turnos contra PostgreSQL real (sin JWT). */
@IntegracionPostgres
@AutoConfigureMockMvc
class TurnoPublicoApiIT {

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

    private Prestacion prestacion;
    private Paciente paciente;

    @BeforeEach
    void preparar() {
        paciente = pacientes.saveAndFlush(new Paciente("Ana", "Ejemplo", "99999997"));
        prestacion = prestaciones.saveAndFlush(new Prestacion("Consulta publica", 30));
        horarios.saveAndFlush(new HorarioAtencion(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre IN ('Consulta publica', 'Inactiva publica')");
        jdbc.update("DELETE FROM horario_atencion WHERE dia_semana = 6");
    }

    @Test
    void reservaValidaDevuelve201ConElResumenDelContrato() throws Exception {
        String cuerpo = mvc.perform(post("/api/public/turnos")
                .contentType(MediaType.APPLICATION_JSON).content(body(proximoSabado(), "99.999.997", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.nombrePaciente").value("Ana"))
                .andExpect(jsonPath("$.puedeConfirmar").value(false))
                .andExpect(jsonPath("$.puedeCancelar").value(true))
                .andExpect(jsonPath("$.prestacion.duracionMinutos").value(30))
                .andExpect(jsonPath("$.contactoConsultorio").value(app.consultorio().contacto()))
                .andReturn().getResponse().getContentAsString();

        assertThat(claves(json.readTree(cuerpo)))
                .isEqualTo(clavesDelEjemplo("turnos-publico-reservar.201.json"));
        Turno guardado = turnos.findAll().get(0);
        assertThat(guardado.getConsentimientoAt()).isNotNull();
        assertThat(guardado.getToken()).isNotBlank();
        assertThat(pacientes.findByDni("99999997").orElseThrow().getNombre()).isEqualTo("Ana");
    }

    @Test
    void sinConsentimientoEs422YNoCreaElTurno() throws Exception {
        mvc.perform(post("/api/public/turnos").contentType(MediaType.APPLICATION_JSON)
                .content(body(proximoSabado(), "99999996", false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONSENTIMIENTO_REQUERIDO"));

        assertThat(turnos.count()).isZero();
    }

    @Test
    void camposInvalidosDan400ConDetalles() throws Exception {
        String inicioSinOffset = proximoSabado().format(FORMATO).replaceAll("[+-]\\d{2}:\\d{2}$", "");

        mvc.perform(post("/api/public/turnos").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Ana","apellido":"Ejemplo","dni":"99999996",
                         "email":"no-es-email","telefono":"+54 9 11 0000-0000",
                         "prestacionId":%d,"inicio":"%s","consentimiento":true}
                        """.formatted(prestacion.getId(), inicioSinOffset)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[?(@.campo == 'email')]").exists())
                .andExpect(jsonPath("$.detalles[?(@.campo == 'inicio')]").exists());
    }

    @Test
    void horarioOcupadoEs422EnLaValidacionPrevia() throws Exception {
        mvc.perform(post("/api/public/turnos").contentType(MediaType.APPLICATION_JSON)
                .content(body(proximoSabado(), "99999996", true)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/public/turnos").contentType(MediaType.APPLICATION_JSON)
                .content(body(proximoSabado(), "99999995", true)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("HORARIO_NO_DISPONIBLE"));
    }

    @Test
    void turnoCanceladoNoBloqueaLaReserva() throws Exception {
        OffsetDateTime inicio = proximoSabado();
        turnos.saveAndFlush(new Turno(paciente, prestacion,
                inicio.toInstant(), inicio.plusMinutes(30).toInstant(), EstadoTurno.CANCELADO,
                "token-cancelado-de-prueba-123456789012345", "previo@ejemplo.com", "11 5555-5555"));

        mvc.perform(post("/api/public/turnos").contentType(MediaType.APPLICATION_JSON)
                .content(body(inicio, "99999996", true)))
                .andExpect(status().isCreated());
    }

    @Test
    void fueraDeLaVentanaPublicaEs422() throws Exception {
        OffsetDateTime inicio = OffsetDateTime.now(app.zonaHoraria()).plusHours(1);

        mvc.perform(post("/api/public/turnos").contentType(MediaType.APPLICATION_JSON)
                .content(body(inicio, "99999996", true)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("HORARIO_NO_DISPONIBLE"));
    }

    @Test
    void prestacionInexistenteEs404() throws Exception {
        String contenido = """
                {"nombre":"Ana","apellido":"Ejemplo","dni":"99999996",
                 "email":"ana@ejemplo.com","telefono":"+54 9 11 0000-0000",
                 "prestacionId":999999,"inicio":"%s","consentimiento":true}
                """.formatted(proximoSabado().format(FORMATO));

        mvc.perform(post("/api/public/turnos").contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void prestacionInactivaEs422() throws Exception {
        Prestacion inactiva = prestaciones.saveAndFlush(new Prestacion("Inactiva publica", 30));
        inactiva.setActiva(false);
        prestaciones.saveAndFlush(inactiva);

        String contenido = """
                {"nombre":"Ana","apellido":"Ejemplo","dni":"99999996",
                 "email":"ana@ejemplo.com","telefono":"+54 9 11 0000-0000",
                 "prestacionId":%d,"inicio":"%s","consentimiento":true}
                """.formatted(inactiva.getId(), proximoSabado().format(FORMATO));

        mvc.perform(post("/api/public/turnos").contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PRESTACION_INACTIVA"));
    }

    private OffsetDateTime proximoSabado() {
        LocalDate dia = LocalDate.now(app.zonaHoraria()).plusDays(1);
        while (dia.getDayOfWeek() != DayOfWeek.SATURDAY) {
            dia = dia.plusDays(1);
        }
        return dia.atTime(10, 0).atZone(app.zonaHoraria()).toOffsetDateTime();
    }

    private String body(OffsetDateTime inicio, String dni, boolean consentimiento) {
        return """
                {"nombre":"Otro Nombre","apellido":"Ejemplo","dni":"%s",
                 "email":"ana@ejemplo.com","telefono":"+54 9 11 0000-0000",
                 "prestacionId":%d,"inicio":"%s","consentimiento":%b}
                """.formatted(dni, prestacion.getId(), inicio.format(FORMATO), consentimiento);
    }

    private static Set<String> claves(JsonNode nodo) {
        Set<String> claves = new TreeSet<>();
        for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
            claves.add(it.next());
        }
        return claves;
    }

    private Set<String> clavesDelEjemplo(String archivo) throws Exception {
        return claves(json.readTree(Files.readString(EJEMPLOS.resolve(archivo))));
    }
}
