package ar.turnos.odontologia.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.horario.HorarioAtencionRepository;
import ar.turnos.odontologia.prestacion.PrestacionRepository;
import ar.turnos.odontologia.seed.DataSeeder;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@IntegracionPostgres
@AutoConfigureMockMvc
class AuthFlowIT {

    private static final String EMAIL_SIN_NORMALIZAR = "  Odontologo@Ejemplo.com  ";
    private static final String EMAIL_NORMALIZADO = "odontologo@ejemplo.com";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DataSeeder seeder;

    @Autowired
    private OdontologoRepository odontologos;

    @Autowired
    private PrestacionRepository prestaciones;

    @Autowired
    private HorarioAtencionRepository horarios;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppProperties app;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void sembrarConEmailSinNormalizar() {
        SecurityContextHolder.clearContext();
        vaciarTodo();
        AppProperties otro = new AppProperties(app.zonaHoraria(), app.frontendBaseUrl(), app.agenda(),
                app.recordatorio(), app.alerta(), app.cancelacion(), app.turno(), app.consultorio(), app.mail(),
                app.jwt(), new AppProperties.Seed(new AppProperties.Seed.Odontologo(
                        app.seed().odontologo().nombre(), EMAIL_SIN_NORMALIZAR,
                        app.seed().odontologo().password())));
        new DataSeeder(odontologos, prestaciones, horarios, otro, passwordEncoder).run();
    }

    @AfterEach
    void restaurarSeed() {
        SecurityContextHolder.clearContext();
        vaciarTodo();
        seeder.run();
    }

    @Test
    void seedSinNormalizarLoginConVariantesTokenEnRutaPrivadaYMe() throws Exception {
        assertThat(jdbc.queryForObject("SELECT 1 FROM pg_class LIMIT 1", Integer.class)).isEqualTo(1);
        assertThat(odontologos.findAll()).singleElement()
                .satisfies(o -> assertThat(o.getEmail()).isEqualTo(EMAIL_NORMALIZADO));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno", Integer.class)).isZero();
        String hashAntes = odontologos.findByEmail(EMAIL_NORMALIZADO).orElseThrow().getPasswordHash();

        MvcResult login = mvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}",
                        EMAIL_SIN_NORMALIZAR, app.seed().odontologo().password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.nombre").value(app.seed().odontologo().nombre()))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.token");

        mvc.perform(get("/api/turnos").header("Authorization", "Bearer " + token))
                .andExpect(status().is(not(401)))
                .andExpect(header().doesNotExist("Set-Cookie"));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value(app.seed().odontologo().nombre()))
                .andExpect(jsonPath("$.email").value(EMAIL_NORMALIZADO))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));

        assertThat(odontologos.count()).isEqualTo(1);
        assertThat(odontologos.findByEmail(EMAIL_NORMALIZADO).orElseThrow().getPasswordHash()).isEqualTo(hashAntes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo", Integer.class)).isZero();
    }

    @Test
    void tokenVencidoManipuladoYRutaPublicaSinTokenNoFiltranSecretos() throws Exception {
        String valido = jwt(Clock.systemUTC()).emitir(EMAIL_NORMALIZADO).token();
        String vencido = jwt(Clock.fixed(Instant.now().minusSeconds(9 * 3600), ZoneOffset.UTC))
                .emitir(EMAIL_NORMALIZADO).token();
        int inicioFirma = valido.lastIndexOf('.') + 1;
        char original = valido.charAt(inicioFirma);
        String manipulado = valido.substring(0, inicioFirma) + (original == 'A' ? 'B' : 'A')
                + valido.substring(inicioFirma + 1);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.addAppender(appender);
        try {
            for (String token : new String[] { vencido, manipulado }) {
                String cuerpo = mvc.perform(get("/api/turnos").header("Authorization", "Bearer " + token))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                        .andExpect(jsonPath("$.mensaje").value("Tenés que iniciar sesión para continuar."))
                        .andExpect(header().doesNotExist("Set-Cookie"))
                        .andReturn().getResponse().getContentAsString();
                assertThat(cuerpo).doesNotContain(token, app.jwt().secret());
            }

            mvc.perform(get("/api/public/prestaciones"))
                    .andExpect(status().is(not(401)))
                    .andExpect(header().doesNotExist("Set-Cookie"));

            assertThat(appender.list).noneMatch(evento -> {
                String mensaje = evento.getFormattedMessage();
                return mensaje.contains(vencido) || mensaje.contains(manipulado)
                        || mensaje.contains(app.jwt().secret());
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    private JwtService jwt(Clock clock) {
        return new JwtService(app, clock);
    }

    private void vaciarTodo() {
        jdbc.update("TRUNCATE turno, paciente, bloqueo RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM horario_atencion");
        jdbc.update("DELETE FROM prestacion");
        jdbc.update("DELETE FROM odontologo");
    }
}
