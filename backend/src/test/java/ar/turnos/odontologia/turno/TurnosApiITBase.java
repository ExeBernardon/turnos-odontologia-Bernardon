package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.auth.JwtService;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.notificacion.NotificacionTurnos;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.paciente.PacienteRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;

/** Fixtures compartidos de los IT de agenda y acciones: reloj fijo, JWT y turnos ficticios. */
@IntegracionPostgres
@AutoConfigureMockMvc
@Import(TurnosApiITBase.RelojFijo.class)
abstract class TurnosApiITBase {

    static final Instant AHORA = Instant.parse("2026-12-01T12:00:00Z");
    static final ZoneOffset OFFSET = ZoneOffset.ofHours(-3);
    static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    @TestConfiguration(proxyBeanMethods = false)
    static class RelojFijo {
        @Bean
        @Primary
        Clock clockFijo() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected AppProperties app;
    @Autowired
    protected TurnoRepository turnos;
    @Autowired
    protected PacienteRepository pacientes;
    @Autowired
    protected PrestacionRepository prestaciones;
    @MockitoBean
    protected NotificacionTurnos notificaciones;

    protected Paciente paciente;
    protected Prestacion prestacion;

    @BeforeEach
    void preparar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre = 'Acciones IT'");
        paciente = pacientes.saveAndFlush(new Paciente("Ana", "Ejemplo", "99999999"));
        prestacion = prestaciones.saveAndFlush(new Prestacion("Acciones IT", 30));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre = 'Acciones IT'");
    }

    protected static Instant en(long horas) {
        return AHORA.plus(Duration.ofHours(horas));
    }

    protected static String local(Instant instante) {
        return instante.atOffset(OFFSET).format(FORMATO);
    }

    protected String token() {
        return new JwtService(app, Clock.fixed(AHORA, ZoneOffset.UTC))
                .emitir(app.seed().odontologo().email()).token();
    }

    protected ResultActions autenticado(MockHttpServletRequestBuilder peticion) throws Exception {
        return mvc.perform(peticion.header("Authorization", "Bearer " + token()));
    }

    protected Turno guardar(Instant inicio, EstadoTurno estado, String sufijo) {
        return turnos.saveAndFlush(new Turno(paciente, prestacion, inicio,
                inicio.plus(Duration.ofMinutes(30)), estado, "token-" + sufijo,
                "paciente@ejemplo.com", "+54 9 11 0000-0000"));
    }

    protected MediaType jsonType() {
        return MediaType.APPLICATION_JSON;
    }

    protected void assertMensaje(String cuerpo, String esperado) throws Exception {
        assertThat(json.readTree(cuerpo).get("mensaje").asText()).isEqualTo(esperado);
    }

    protected String cuerpo(String respuesta) {
        return respuesta;
    }
}
