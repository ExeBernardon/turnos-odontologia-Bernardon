package ar.turnos.odontologia.bloqueo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.turnos.odontologia.IntegracionPostgres;

@IntegracionPostgres
class BloqueoRepositoryIT {

    @Autowired
    private BloqueoRepository bloqueos;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE bloqueo RESTART IDENTITY");
    }

    private static Instant a(int hora, int minuto) {
        return OffsetDateTime.parse("2026-11-25T00:00:00-03:00").plusHours(hora).plusMinutes(minuto).toInstant();
    }

    @Test
    void incluyeElQueCruzaElBordeYExcluyeLosQueSoloTocan() {
        bloqueos.save(new Bloqueo(a(8, 0), a(9, 30), "Cruza el borde"));
        bloqueos.save(new Bloqueo(a(7, 0), a(9, 0), "Termina justo al inicio"));
        bloqueos.save(new Bloqueo(a(12, 0), a(13, 0), "Empieza justo al final"));
        bloqueos.flush();

        assertThat(bloqueos.findQueIntersecan(a(9, 0), a(12, 0)))
                .extracting(Bloqueo::getMotivo)
                .containsExactly("Cruza el borde");
    }

    @Test
    void devuelveLosContenidosOrdenadosPorInicio() {
        bloqueos.save(new Bloqueo(a(11, 0), a(11, 30), "Segundo"));
        bloqueos.save(new Bloqueo(a(10, 0), a(10, 30), "Primero"));
        bloqueos.save(new Bloqueo(a(8, 0), a(14, 0), "Envuelve todo"));
        bloqueos.flush();

        assertThat(bloqueos.findQueIntersecan(a(9, 0), a(12, 0)))
                .extracting(Bloqueo::getMotivo)
                .containsExactly("Envuelve todo", "Primero", "Segundo");
    }

    @Test
    void motivoAdmiteNuloYElInstanteSeConserva() {
        Bloqueo guardado = bloqueos.saveAndFlush(new Bloqueo(a(9, 0), a(10, 0), null));

        Bloqueo leido = bloqueos.findById(guardado.getId()).orElseThrow();

        assertThat(leido.getMotivo()).isNull();
        assertThat(leido.getInicio()).isEqualTo(Instant.parse("2026-11-25T12:00:00Z"));
        assertThat(leido.getCreadoEn()).isNotNull();
    }
}
