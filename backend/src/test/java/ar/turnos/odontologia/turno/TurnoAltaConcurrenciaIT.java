package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.horario.HorarioAtencion;
import ar.turnos.odontologia.horario.HorarioAtencionRepository;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.paciente.PacienteRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;

/** La restriccion EXCLUDE de PostgreSQL es la garantia final: una reserva gana, la otra es 409. */
@IntegracionPostgres
@AutoConfigureMockMvc
class TurnoAltaConcurrenciaIT {

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

    @BeforeEach
    void preparar() {
        pacientes.saveAndFlush(new Paciente("Ana", "Ejemplo", "99999998"));
        prestacion = prestaciones.saveAndFlush(new Prestacion("Prueba concurrencia", 30));
        horarios.saveAndFlush(new HorarioAtencion(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE id = ?", prestacion.getId());
        jdbc.update("DELETE FROM horario_atencion WHERE dia_semana = 6");
    }

    @Test
    void dosReservasSimultaneasSoloUnaSeQuedaConElHorario() throws Exception {
        String body = body(proximoSabado());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        Callable<Respuesta> tarea = () -> {
            salida.await();
            return reservar(body);
        };

        try {
            Future<Respuesta> primero = pool.submit(tarea);
            Future<Respuesta> segundo = pool.submit(tarea);
            salida.countDown();

            Respuesta r1 = primero.get(60, TimeUnit.SECONDS);
            Respuesta r2 = segundo.get(60, TimeUnit.SECONDS);

            assertThat(List.of(r1.estado(), r2.estado())).containsExactlyInAnyOrder(201, 409);
            Respuesta perdedora = r1.estado() == 409 ? r1 : r2;
            assertThat(json.readTree(perdedora.cuerpo()).get("codigo").asText())
                    .isEqualTo("HORARIO_OCUPADO");
            assertThat(turnos.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private OffsetDateTime proximoSabado() {
        LocalDate dia = LocalDate.now(app.zonaHoraria()).plusDays(1);
        while (dia.getDayOfWeek() != DayOfWeek.SATURDAY) {
            dia = dia.plusDays(1);
        }
        return dia.atTime(10, 0).atZone(app.zonaHoraria()).toOffsetDateTime();
    }

    private String body(OffsetDateTime inicio) {
        return """
                {"nombre":"Ana","apellido":"Ejemplo","dni":"99.999.998",
                 "email":"ana@ejemplo.com","telefono":"+54 9 11 0000-0000",
                 "prestacionId":%d,"inicio":"%s","consentimiento":true}
                """.formatted(prestacion.getId(), inicio.format(FORMATO));
    }

    private Respuesta reservar(String body) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/public/turnos")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        return new Respuesta(resultado.getResponse().getStatus(), resultado.getResponse().getContentAsString());
    }

    private record Respuesta(int estado, String cuerpo) {
    }
}
