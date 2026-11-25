package ar.turnos.odontologia.bloqueo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.turno.EstadoTurno;
import ar.turnos.odontologia.turno.Turno;

class BloqueoMapperTest {

    private static final Instant AHORA = Instant.parse("2026-12-24T12:00:00Z");
    private final BloqueoMapper mapper = new BloqueoMapper(Clock.fixed(AHORA, ZoneOffset.UTC),
            ZoneId.of("America/Argentina/Buenos_Aires"));

    private static Turno turno(EstadoTurno estado, Instant inicio) {
        return new Turno(new Paciente("Ana", "Ejemplo", "99999999"), new Prestacion("Consulta", 30), inicio,
                inicio.plusSeconds(1800), estado, "tok", "paciente@ejemplo.com", "+54 9 11 0000-0000");
    }

    @Test
    void instantesSalenConOffsetDeBuenosAires() {
        BloqueoResponse r = mapper.aBloqueo(new Bloqueo(Instant.parse("2026-12-24T12:00:00Z"),
                Instant.parse("2026-12-24T16:00:00Z"), "m"));

        assertThat(r.inicio().toString()).isEqualTo("2026-12-24T09:00-03:00");
        assertThat(r.fin().getOffset().getTotalSeconds()).isEqualTo(-3 * 3600);
    }

    @Test
    void pendienteMenos24hSoloSiEstaPendienteYEmpiezaEnMenosDe24h() {
        assertThat(mapper.aTurno(turno(EstadoTurno.PENDIENTE, AHORA.plusSeconds(23 * 3600))).pendienteMenos24h())
                .isTrue();
        assertThat(mapper.aTurno(turno(EstadoTurno.PENDIENTE, AHORA.plusSeconds(25 * 3600))).pendienteMenos24h())
                .isFalse();
        assertThat(mapper.aTurno(turno(EstadoTurno.CONFIRMADO, AHORA.plusSeconds(3600))).pendienteMenos24h())
                .isFalse();
        assertThat(mapper.aTurno(turno(EstadoTurno.PENDIENTE, AHORA.minusSeconds(3600))).pendienteMenos24h())
                .isFalse();
    }
}
