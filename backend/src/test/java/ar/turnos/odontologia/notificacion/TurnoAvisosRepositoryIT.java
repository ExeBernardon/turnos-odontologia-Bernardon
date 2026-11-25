package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.paciente.PacienteRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;
import ar.turnos.odontologia.turno.EstadoTurno;
import ar.turnos.odontologia.turno.Turno;
import ar.turnos.odontologia.turno.TurnoRepository;

/** Seleccion y marcado de los avisos programados contra PostgreSQL real. */
@IntegracionPostgres
class TurnoAvisosRepositoryIT {

    /** Lunes 09:00 en Buenos Aires. */
    private static final Instant AHORA = Instant.parse("2026-12-07T12:00:00Z");
    private static final Duration H48 = Duration.ofHours(48);
    private static final Duration H24 = Duration.ofHours(24);

    @Autowired
    private TurnoAvisosRepository avisos;
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
        paciente = pacientes.saveAndFlush(new Paciente("Ana", "Ejemplo", "99999999"));
        prestacion = prestaciones.saveAndFlush(new Prestacion("Avisos repo IT", 30));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre = 'Avisos repo IT'");
    }

    private Turno guardar(Instant inicio, EstadoTurno estado, boolean recordatorio, boolean alerta) {
        Turno turno = new Turno(paciente, prestacion, inicio, inicio.plus(Duration.ofMinutes(30)), estado,
                "token-avisos-" + (++secuencia), "paciente@ejemplo.com", "1155555555");
        turno.setRecordatorioEnviado(recordatorio);
        turno.setAlertaPendienteEnviada(alerta);
        return turnos.saveAndFlush(turno);
    }

    private Turno pendiente(Instant inicio) {
        return guardar(inicio, EstadoTurno.PENDIENTE, false, false);
    }

    private List<Long> idsRecordatorio() {
        return avisos.idsParaRecordatorio(AHORA, AHORA.plus(H48));
    }

    private List<Long> idsAlerta() {
        return avisos.idsParaAlerta(AHORA, AHORA.plus(H24));
    }

    private Turno releer(Turno turno) {
        return turnos.findById(turno.getId()).orElseThrow();
    }

    // --- recordatorio ---

    @Test
    void recordatorioEligeElBordeExactoDe48HYLosDe30HOrdenadosPorInicio() {
        Turno aLas48 = pendiente(AHORA.plus(H48));
        Turno aLas30 = pendiente(AHORA.plus(Duration.ofHours(30)));

        assertThat(idsRecordatorio()).containsExactly(aLas30.getId(), aLas48.getId());
    }

    @Test
    void recordatorioNoEligeNadaFueraDeLaVentanaNiEnOtroEstadoNiConFlagNiYaEmpezado() {
        pendiente(AHORA.plus(H48).plusSeconds(60));
        pendiente(AHORA.minus(Duration.ofMinutes(10)));
        guardar(AHORA.plus(Duration.ofHours(30)), EstadoTurno.CONFIRMADO, false, false);
        guardar(AHORA.plus(Duration.ofHours(31)), EstadoTurno.CANCELADO, false, false);
        guardar(AHORA.plus(Duration.ofHours(32)), EstadoTurno.ASISTIO, false, false);
        guardar(AHORA.plus(Duration.ofHours(33)), EstadoTurno.PENDIENTE, true, false);

        assertThat(idsRecordatorio()).isEmpty();
    }

    @Test
    void recordatorioConHastaMasLejanoIncluyeTurnosDeLaVentanaAmpliada() {
        Turno aLas60 = pendiente(AHORA.plus(Duration.ofHours(60)));

        assertThat(avisos.idsParaRecordatorio(AHORA, AHORA.plus(Duration.ofHours(72))))
                .containsExactly(aLas60.getId());
        assertThat(idsRecordatorio()).isEmpty();
    }

    // --- alerta ---

    @Test
    void alertaEligeUnTurnoPendienteA23H59() {
        Turno apenasDentro = pendiente(AHORA.plus(H24).minusSeconds(60));

        assertThat(idsAlerta()).containsExactly(apenasDentro.getId());
    }

    @Test
    void alertaNoEligeElBordeDe24HNiConfirmadosNiConFlagNiYaEmpezados() {
        pendiente(AHORA.plus(H24));
        pendiente(AHORA.minus(Duration.ofMinutes(10)));
        guardar(AHORA.plus(Duration.ofHours(10)), EstadoTurno.CONFIRMADO, true, false);
        guardar(AHORA.plus(Duration.ofHours(11)), EstadoTurno.PENDIENTE, true, true);
        guardar(AHORA.plus(Duration.ofHours(12)), EstadoTurno.CANCELADO, true, false);

        assertThat(idsAlerta()).isEmpty();
    }

    @Test
    void alertaNoDependeDelFlagDelRecordatorioYSeOrdenaPorInicio() {
        Turno tarde = guardar(AHORA.plus(Duration.ofHours(20)), EstadoTurno.PENDIENTE, false, false);
        Turno temprano = guardar(AHORA.plus(Duration.ofHours(5)), EstadoTurno.PENDIENTE, true, false);

        assertThat(idsAlerta()).containsExactly(temprano.getId(), tarde.getId());
    }

    // --- UPDATE condicionado ---

    @Test
    void marcarRecordatorioConPendienteYMismoInicioAfectaUnaFila() {
        Turno turno = pendiente(AHORA.plus(Duration.ofHours(30)));

        int filas = avisos.marcarRecordatorioEnviado(turno.getId(), turno.getInicio());

        assertThat(filas).isEqualTo(1);
        assertThat(releer(turno).isRecordatorioEnviado()).isTrue();
        assertThat(releer(turno).isAlertaPendienteEnviada()).isFalse();
    }

    @Test
    void marcarAlertaConPendienteYMismoInicioAfectaUnaFila() {
        Turno turno = pendiente(AHORA.plus(Duration.ofHours(10)));

        int filas = avisos.marcarAlertaEnviada(turno.getId(), turno.getInicio());

        assertThat(filas).isEqualTo(1);
        assertThat(releer(turno).isAlertaPendienteEnviada()).isTrue();
        assertThat(releer(turno).isRecordatorioEnviado()).isFalse();
    }

    @Test
    void marcarNoAfectaNadaSiElTurnoSeConfirmoOSeCancelo() {
        Turno confirmado = guardar(AHORA.plus(Duration.ofHours(30)), EstadoTurno.CONFIRMADO, false, false);
        Turno cancelado = guardar(AHORA.plus(Duration.ofHours(31)), EstadoTurno.CANCELADO, false, false);

        assertThat(avisos.marcarRecordatorioEnviado(confirmado.getId(), confirmado.getInicio())).isZero();
        assertThat(avisos.marcarAlertaEnviada(confirmado.getId(), confirmado.getInicio())).isZero();
        assertThat(avisos.marcarRecordatorioEnviado(cancelado.getId(), cancelado.getInicio())).isZero();
        assertThat(avisos.marcarAlertaEnviada(cancelado.getId(), cancelado.getInicio())).isZero();

        Turno releidoConfirmado = releer(confirmado);
        assertThat(releidoConfirmado.getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);
        assertThat(releidoConfirmado.isRecordatorioEnviado()).isFalse();
        assertThat(releidoConfirmado.isAlertaPendienteEnviada()).isFalse();
        assertThat(releer(cancelado).getEstado()).isEqualTo(EstadoTurno.CANCELADO);
    }

    @Test
    void marcarNoAfectaNadaSiElInicioCambioYConservaElInicioNuevo() {
        Turno turno = pendiente(AHORA.plus(Duration.ofHours(30)));
        Instant inicioViejo = turno.getInicio();
        Instant inicioNuevo = AHORA.plus(Duration.ofDays(5));
        jdbc.update("UPDATE turno SET inicio = ?, fin = ? WHERE id = ?", Timestamp.from(inicioNuevo),
                Timestamp.from(inicioNuevo.plus(Duration.ofMinutes(30))), turno.getId());

        assertThat(avisos.marcarRecordatorioEnviado(turno.getId(), inicioViejo)).isZero();
        assertThat(avisos.marcarAlertaEnviada(turno.getId(), inicioViejo)).isZero();

        Turno releido = releer(turno);
        assertThat(releido.getInicio()).isEqualTo(inicioNuevo);
        assertThat(releido.getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
        assertThat(releido.isRecordatorioEnviado()).isFalse();
        assertThat(releido.isAlertaPendienteEnviada()).isFalse();
    }

    @Test
    void marcarDosVecesElMismoFlagLaSegundaAfectaCeroFilas() {
        Turno turno = pendiente(AHORA.plus(Duration.ofHours(30)));

        assertThat(avisos.marcarRecordatorioEnviado(turno.getId(), turno.getInicio())).isEqualTo(1);
        assertThat(avisos.marcarRecordatorioEnviado(turno.getId(), turno.getInicio())).isZero();
    }

    // --- indice ---

    /**
     * El EXPLAIN usa SQL equivalente a las consultas JPQL (mismas columnas y operadores): verifica que el
     * indice (estado, inicio) sirve a ese predicado; no inspecciona el SQL que genera Hibernate.
     */
    @Test
    @Transactional
    void lasConsultasDeSeleccionPuedenUsarTurnoEstadoInicioIdx() {
        pendiente(AHORA.plus(Duration.ofHours(30)));
        pendiente(AHORA.plus(Duration.ofHours(10)));
        // Volumen historico (turnos ya cerrados) para que el planificador tenga con que elegir.
        jdbc.update("INSERT INTO turno (paciente_id, prestacion_id, inicio, fin, estado, token, email_contacto, "
                + "telefono_contacto, recordatorio_enviado, alerta_pendiente_enviada, creado_en, actualizado_en) "
                + "SELECT ?, ?, TIMESTAMPTZ '2025-01-01 12:00:00+00' + g * INTERVAL '1 hour', "
                + "TIMESTAMPTZ '2025-01-01 12:30:00+00' + g * INTERVAL '1 hour', 'ASISTIO', 'hist-' || g, "
                + "'paciente@ejemplo.com', '1155555555', true, true, now(), now() FROM generate_series(1, 3000) g",
                paciente.getId(), prestacion.getId());
        jdbc.execute("SET LOCAL enable_seqscan = off");
        jdbc.execute("ANALYZE turno");

        String planRecordatorio = plan("estado = 'PENDIENTE' AND recordatorio_enviado = false "
                + "AND inicio > '2026-12-07T12:00:00Z' AND inicio <= '2026-12-09T12:00:00Z'");
        String planAlerta = plan("estado = 'PENDIENTE' AND alerta_pendiente_enviada = false "
                + "AND inicio > '2026-12-07T12:00:00Z' AND inicio < '2026-12-08T12:00:00Z'");

        assertThat(planRecordatorio).contains("turno_estado_inicio_idx");
        assertThat(planAlerta).contains("turno_estado_inicio_idx");
    }

    private String plan(String condicion) {
        return String.join("\n", jdbc.queryForList(
                "EXPLAIN SELECT id FROM turno WHERE " + condicion + " ORDER BY inicio", String.class));
    }
}
