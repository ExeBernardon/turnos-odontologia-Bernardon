package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.horario.HorarioAtencion;
import ar.turnos.odontologia.horario.HorarioAtencionRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;
import ar.turnos.odontologia.turno.TurnoRepository;

/** Con el SMTP caido la reserva publica igual responde 201, guarda el turno y no se cuelga. */
@IntegracionPostgres
@AutoConfigureMockMvc
class ReservaConSmtpCaidoIT {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    @DynamicPropertySource
    static void smtpSinServidor(DynamicPropertyRegistry registry) throws IOException {
        int puertoCerrado;
        try (ServerSocket socket = new ServerSocket(0)) {
            puertoCerrado = socket.getLocalPort();
        }
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> puertoCerrado);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AppProperties app;
    @Autowired
    private TurnoRepository turnos;
    @Autowired
    private PrestacionRepository prestaciones;
    @Autowired
    private HorarioAtencionRepository horarios;

    private Prestacion prestacion;

    @BeforeEach
    void preparar() {
        prestacion = prestaciones.saveAndFlush(new Prestacion("Consulta smtp caido", 30));
        horarios.saveAndFlush(new HorarioAtencion(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre = 'Consulta smtp caido'");
        jdbc.update("DELETE FROM horario_atencion WHERE dia_semana = 6");
    }

    @Test
    void laReservaResponde201GuardaElTurnoYTerminaRapido() throws Exception {
        long inicio = System.nanoTime();

        mvc.perform(post("/api/public/turnos").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("99999994", proximoSabado(10))))
                .andExpect(status().isCreated());

        long milis = (System.nanoTime() - inicio) / 1_000_000;
        assertThat(turnos.count()).isEqualTo(1);
        assertThat(milis).isLessThan(3000);
    }

    @Test
    void unaSegundaReservaTambienSeGuardaAunqueNingunMailSalga() throws Exception {
        mvc.perform(post("/api/public/turnos").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("99999994", proximoSabado(10)))).andExpect(status().isCreated());
        mvc.perform(post("/api/public/turnos").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("99999993", proximoSabado(11)))).andExpect(status().isCreated());

        assertThat(turnos.count()).isEqualTo(2);
    }

    private OffsetDateTime proximoSabado(int hora) {
        LocalDate dia = LocalDate.now(app.zonaHoraria()).plusDays(3);
        while (dia.getDayOfWeek() != DayOfWeek.SATURDAY) {
            dia = dia.plusDays(1);
        }
        return dia.atTime(hora, 0).atZone(app.zonaHoraria()).toOffsetDateTime();
    }

    private String cuerpo(String dni, OffsetDateTime inicio) {
        return """
                {"nombre":"Ana","apellido":"Ejemplo","dni":"%s",
                 "email":"paciente@ejemplo.com","telefono":"1155555555",
                 "prestacionId":%d,"inicio":"%s","consentimiento":true}
                """.formatted(dni, prestacion.getId(), inicio.format(FORMATO));
    }
}
