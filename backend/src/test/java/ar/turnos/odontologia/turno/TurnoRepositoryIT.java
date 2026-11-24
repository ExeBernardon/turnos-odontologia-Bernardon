package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.paciente.PacienteRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;

@IntegracionPostgres
class TurnoRepositoryIT {

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
        paciente = pacientes.saveAndFlush(new Paciente("Ana", "Prueba", "99999999"));
        prestacion = prestaciones.saveAndFlush(new Prestacion("Prueba turno", 30));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre = 'Prueba turno'");
    }

    private static Instant a(int hora, int minuto) {
        return OffsetDateTime.parse("2026-11-25T00:00:00-03:00").plusHours(hora).plusMinutes(minuto).toInstant();
    }

    private Turno turno(int hIni, int mIni, int hFin, int mFin, EstadoTurno estado, String token) {
        return new Turno(paciente, prestacion, a(hIni, mIni), a(hFin, mFin), estado, token,
                "paciente@ejemplo.com", "11 5555-5555");
    }

    @Test
    void instanteConOffsetSeLeeComoElMismoInstante() {
        OffsetDateTime local = OffsetDateTime.parse("2026-11-25T10:00:00-03:00");
        Turno guardado = turnos.saveAndFlush(new Turno(paciente, prestacion, local.toInstant(),
                local.plusMinutes(30).toInstant(), EstadoTurno.PENDIENTE, "tok-instante", "paciente@ejemplo.com",
                "11 5555-5555"));

        Turno leido = turnos.findById(guardado.getId()).orElseThrow();

        assertThat(leido.getInicio()).isEqualTo(Instant.parse("2026-11-25T13:00:00Z"));
        assertThat(leido.getFin()).isEqualTo(Instant.parse("2026-11-25T13:30:00Z"));
    }

    @Test
    void lasColumnasDeInstanteSonTimestamptzYLaHoraDelHorarioEsTime() {
        assertThat(tipo("turno", "inicio")).isEqualTo("timestamp with time zone");
        assertThat(tipo("turno", "fin")).isEqualTo("timestamp with time zone");
        assertThat(tipo("turno", "consentimiento_at")).isEqualTo("timestamp with time zone");
        assertThat(tipo("turno", "creado_en")).isEqualTo("timestamp with time zone");
        assertThat(tipo("turno", "actualizado_en")).isEqualTo("timestamp with time zone");
        assertThat(tipo("bloqueo", "inicio")).isEqualTo("timestamp with time zone");
        assertThat(tipo("horario_atencion", "hora_inicio")).isEqualTo("time without time zone");
    }

    private String tipo(String tabla, String columna) {
        return jdbc.queryForObject("SELECT data_type FROM information_schema.columns "
                + "WHERE table_name = ? AND column_name = ?", String.class, tabla, columna);
    }

    @Test
    void estadoYCanceladoPorSeGuardanComoTexto() {
        Turno t = turno(9, 0, 9, 30, EstadoTurno.CANCELADO, "tok-texto");
        t.setCanceladoPor(CanceladoPor.PACIENTE);
        Turno guardado = turnos.saveAndFlush(t);

        String estado = jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, guardado.getId());
        String origen = jdbc.queryForObject("SELECT cancelado_por FROM turno WHERE id = ?", String.class,
                guardado.getId());
        Turno leido = turnos.findById(guardado.getId()).orElseThrow();

        assertThat(estado).isEqualTo("CANCELADO");
        assertThat(origen).isEqualTo("PACIENTE");
        assertThat(leido.getEstado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(leido.getCanceladoPor()).isEqualTo(CanceladoPor.PACIENTE);
    }

    @Test
    void confirmadoSeVeComoTextoEnLaBase() {
        Turno guardado = turnos.saveAndFlush(turno(9, 0, 9, 30, EstadoTurno.CONFIRMADO, "tok-confirmado"));

        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, guardado.getId()))
                .isEqualTo("CONFIRMADO");
    }

    @Test
    void losFlagsNacenEnFalsoYElConsentimientoQuedaNulo() {
        Turno guardado = turnos.saveAndFlush(turno(9, 0, 9, 30, EstadoTurno.PENDIENTE, "tok-flags"));

        Turno leido = turnos.findById(guardado.getId()).orElseThrow();

        assertThat(leido.isRecordatorioEnviado()).isFalse();
        assertThat(leido.isAlertaPendienteEnviada()).isFalse();
        assertThat(leido.getConsentimientoAt()).isNull();
        assertThat(leido.getCanceladoPor()).isNull();
        assertThat(leido.getCreadoEn()).isNotNull();
        assertThat(leido.getActualizadoEn()).isNotNull();
    }

    @Test
    void tokenRepetidoFallaPorTurnoTokenUk() {
        turnos.saveAndFlush(turno(9, 0, 9, 30, EstadoTurno.PENDIENTE, "tok-repetido"));

        assertThatThrownBy(() -> turnos.saveAndFlush(turno(10, 0, 10, 30, EstadoTurno.PENDIENTE, "tok-repetido")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("turno_token_uk");
    }

    @Test
    void findByTokenEncuentraYDevuelveVacioSiNoExiste() {
        turnos.saveAndFlush(turno(9, 0, 9, 30, EstadoTurno.PENDIENTE, "tok-busqueda"));

        assertThat(turnos.findByToken("tok-busqueda")).isPresent();
        assertThat(turnos.findByToken("tok-inexistente")).isEmpty();
    }

    @Test
    void activosQueIntersecanDevuelveSoloElActivoInternoDelRango() {
        turnos.save(turno(10, 0, 10, 30, EstadoTurno.PENDIENTE, "tok-a"));
        turnos.save(turno(11, 0, 11, 30, EstadoTurno.CANCELADO, "tok-b"));
        turnos.save(turno(12, 0, 12, 30, EstadoTurno.CONFIRMADO, "tok-c"));
        turnos.save(turno(8, 30, 9, 0, EstadoTurno.CONFIRMADO, "tok-d"));
        turnos.flush();

        List<Turno> r = turnos.findActivosQueIntersecan(a(9, 0), a(12, 0));

        assertThat(r).extracting(Turno::getToken).containsExactly("tok-a");
    }

    @Test
    void activosQueIntersecanIncluyeUnoQueCruzaElBordeYVieneOrdenado() {
        turnos.save(turno(11, 30, 12, 15, EstadoTurno.CONFIRMADO, "tok-e"));
        turnos.save(turno(8, 45, 9, 15, EstadoTurno.PENDIENTE, "tok-f"));
        turnos.save(turno(10, 0, 10, 30, EstadoTurno.AUSENTE, "tok-g"));
        turnos.flush();

        assertThat(turnos.findActivosQueIntersecan(a(9, 0), a(12, 0)))
                .extracting(Turno::getToken).containsExactly("tok-f", "tok-e");
    }
}
