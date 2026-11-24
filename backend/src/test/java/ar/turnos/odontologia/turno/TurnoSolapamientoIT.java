package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.common.ViolacionDeSolapamiento;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.paciente.PacienteRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;

/** Prueba la garantia real de la base (EXCLUDE gist) con PostgreSQL 16; los tests no son transaccionales. */
@IntegracionPostgres
class TurnoSolapamientoIT {

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
    private int secuencia;

    @BeforeEach
    void preparar() {
        paciente = pacientes.saveAndFlush(new Paciente("Ana", "Prueba", "99999999"));
        prestacion = prestaciones.saveAndFlush(new Prestacion("Prueba solapamiento", 30));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre = 'Prueba solapamiento'");
    }

    private static Instant a(int hora, int minuto) {
        return OffsetDateTime.parse("2026-11-25T00:00:00-03:00").plusHours(hora).plusMinutes(minuto).toInstant();
    }

    private Turno turno(int hIni, int mIni, int hFin, int mFin, EstadoTurno estado) {
        return new Turno(paciente, prestacion, a(hIni, mIni), a(hFin, mFin), estado, "tok-solap-" + (++secuencia),
                "paciente@ejemplo.com", "11 5555-5555");
    }

    static boolean tieneSqlState(Throwable e, String estado) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && estado.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void existeUnaSolaRestriccionDeExclusionSobreTurno() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM pg_constraint "
                + "WHERE conname = 'turno_sin_solapamiento' AND contype = 'x' AND conrelid = 'turno'::regclass",
                Integer.class);

        assertThat(n).isEqualTo(1);
    }

    @Test
    void dosActivosQueSePisanFallanConSqlState23P01() {
        turnos.saveAndFlush(turno(9, 0, 9, 30, EstadoTurno.PENDIENTE));

        assertThatThrownBy(() -> turnos.saveAndFlush(turno(9, 15, 9, 45, EstadoTurno.CONFIRMADO)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(tieneSqlState(e, "23P01")).isTrue());
    }

    @Test
    void turnosAdyacentesSeAceptan() {
        turnos.saveAndFlush(turno(9, 0, 9, 30, EstadoTurno.PENDIENTE));

        assertThatCode(() -> turnos.saveAndFlush(turno(9, 30, 10, 0, EstadoTurno.CONFIRMADO)))
                .doesNotThrowAnyException();
    }

    @Test
    void unCanceladoNoOcupaElHorario() {
        turnos.saveAndFlush(turno(9, 0, 9, 30, EstadoTurno.CANCELADO));

        assertThatCode(() -> turnos.saveAndFlush(turno(9, 0, 9, 30, EstadoTurno.PENDIENTE)))
                .doesNotThrowAnyException();
    }

    @Test
    void reactivarUnCanceladoSobreUnHorarioOcupadoFalla() {
        Turno cancelado = turnos.saveAndFlush(turno(9, 0, 9, 30, EstadoTurno.CANCELADO));
        turnos.saveAndFlush(turno(9, 0, 9, 30, EstadoTurno.PENDIENTE));

        Turno reactivado = turnos.findById(cancelado.getId()).orElseThrow();
        reactivado.setEstado(EstadoTurno.PENDIENTE);

        assertThatThrownBy(() -> turnos.saveAndFlush(reactivado))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(tieneSqlState(e, "23P01")).isTrue());
    }

    @Test
    void elHelperReconoceLaExcepcionRealDelSolapamiento() {
        turnos.saveAndFlush(turno(9, 0, 9, 30, EstadoTurno.PENDIENTE));

        Throwable real = org.assertj.core.api.Assertions
                .catchThrowable(() -> turnos.saveAndFlush(turno(9, 10, 9, 40, EstadoTurno.PENDIENTE)));

        assertThat(ViolacionDeSolapamiento.es(real)).isTrue();
    }

    @Test
    void elHelperNoConfundeUnTokenRepetidoConUnSolapamiento() {
        turnos.saveAndFlush(turno(9, 0, 9, 30, EstadoTurno.PENDIENTE));
        Turno repetido = turno(10, 0, 10, 30, EstadoTurno.PENDIENTE);
        repetido.setToken("tok-solap-1");

        Throwable real = org.assertj.core.api.Assertions.catchThrowable(() -> turnos.saveAndFlush(repetido));

        assertThat(real).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(ViolacionDeSolapamiento.es(real)).isFalse();
    }
}
