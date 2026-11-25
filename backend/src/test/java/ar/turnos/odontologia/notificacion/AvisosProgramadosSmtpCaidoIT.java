package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.paciente.PacienteRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;
import ar.turnos.odontologia.turno.EstadoTurno;
import ar.turnos.odontologia.turno.Turno;
import ar.turnos.odontologia.turno.TurnoRepository;

/**
 * Con el SMTP caido y la implementacion real de {@code NotificacionTurnos}, el ciclo termina sin excepcion,
 * rapido y deja los flags en {@code false} para reintentar.
 */
@IntegracionPostgres
@Import(AvisosProgramadosIT.RelojFijo.class)
class AvisosProgramadosSmtpCaidoIT {

    private static final Instant AHORA = AvisosProgramadosIT.AHORA;

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
    private AvisosProgramadosService servicio;
    @Autowired
    private TurnoRepository turnos;
    @Autowired
    private PacienteRepository pacientes;
    @Autowired
    private PrestacionRepository prestaciones;
    @Autowired
    private JdbcTemplate jdbc;

    private Paciente paciente;
    private Prestacion prestacion;

    @BeforeEach
    void preparar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre = 'Avisos SMTP caido IT'");
        paciente = pacientes.saveAndFlush(new Paciente("Ana", "Ejemplo", "99999999"));
        prestacion = prestaciones.saveAndFlush(new Prestacion("Avisos SMTP caido IT", 30));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre = 'Avisos SMTP caido IT'");
    }

    private Turno pendiente(long horas, String token, boolean recordatorio) {
        Instant inicio = AHORA.plus(Duration.ofHours(horas));
        Turno turno = new Turno(paciente, prestacion, inicio, inicio.plus(Duration.ofMinutes(30)),
                EstadoTurno.PENDIENTE, token, "paciente@ejemplo.com", "1155555555");
        turno.setRecordatorioEnviado(recordatorio);
        return turnos.saveAndFlush(turno);
    }

    @Test
    void elCicloTerminaSinExcepcionRapidoYConLosFlagsEnFalse() {
        Turno paraRecordatorio1 = pendiente(30, "token-ficticio-smtp-1", false);
        Turno paraRecordatorio2 = pendiente(31, "token-ficticio-smtp-2", false);
        Turno paraAlerta = pendiente(10, "token-ficticio-smtp-3", true);

        long inicio = System.nanoTime();
        ResultadoCiclo[] resultado = new ResultadoCiclo[1];
        assertThatCode(() -> resultado[0] = servicio.ejecutarCiclo(AHORA)).doesNotThrowAnyException();
        long milis = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(milis).isLessThan(5000);
        assertThat(resultado[0]).isEqualTo(new ResultadoCiclo(0, 2, 0, 1));
        assertThat(turnos.findById(paraRecordatorio1.getId()).orElseThrow().isRecordatorioEnviado()).isFalse();
        assertThat(turnos.findById(paraRecordatorio2.getId()).orElseThrow().isRecordatorioEnviado()).isFalse();
        assertThat(turnos.findById(paraAlerta.getId()).orElseThrow().isAlertaPendienteEnviada()).isFalse();
    }

    @Test
    void unSegundoCicloConElSmtpCaidoReintentaSinAcumularErrores() {
        Turno turno = pendiente(30, "token-ficticio-smtp-4", false);

        servicio.ejecutarCiclo(AHORA);
        ResultadoCiclo segundo = servicio.ejecutarCiclo(AHORA);

        assertThat(segundo).isEqualTo(new ResultadoCiclo(0, 1, 0, 0));
        assertThat(turnos.findById(turno.getId()).orElseThrow().isRecordatorioEnviado()).isFalse();
    }
}
