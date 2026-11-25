package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class FormatoTurnoTest {

    private final FormatoTurno formato = new FormatoTurno(ZoneId.of("America/Argentina/Buenos_Aires"));

    @Test
    void muestraElInicioEnBuenosAiresEnEspanol() {
        String texto = formato.fechaHora(Instant.parse("2026-12-10T13:00:00Z"));

        assertThat(texto).isEqualTo("jueves 10 de diciembre de 2026, 10:00");
    }

    @Test
    void unInicioCercaDeLaMedianocheUtcCaeEnElDiaAnteriorLocal() {
        String texto = formato.fechaHora(Instant.parse("2026-12-11T02:30:00Z"));

        assertThat(texto).isEqualTo("jueves 10 de diciembre de 2026, 23:30");
    }
}
