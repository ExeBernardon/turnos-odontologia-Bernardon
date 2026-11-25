package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinksTurnoTest {

    @Test
    void confirmarYCancelarApuntanAlFrontConElToken() {
        LinksTurno links = new LinksTurno("http://localhost:8080");

        assertThat(links.confirmar("abc123")).isEqualTo("http://localhost:8080/turno/confirmar?token=abc123");
        assertThat(links.cancelar("abc123")).isEqualTo("http://localhost:8080/turno/cancelar?token=abc123");
    }

    @Test
    void otraBaseYOtroTokenSeReflejanEnElLink() {
        LinksTurno links = new LinksTurno("https://turnos.ejemplo.com");

        assertThat(links.cancelar("Zz_-9")).isEqualTo("https://turnos.ejemplo.com/turno/cancelar?token=Zz_-9");
    }

    @Test
    void laBarraFinalDeLaBaseNoDuplicaBarras() {
        LinksTurno links = new LinksTurno("http://localhost:8080/");

        assertThat(links.confirmar("abc123")).isEqualTo("http://localhost:8080/turno/confirmar?token=abc123");
        assertThat(links.reservar()).isEqualTo("http://localhost:8080/reservar");
    }

    @Test
    void elLinkDeReservaNoLlevaToken() {
        assertThat(new LinksTurno("http://localhost:8080").reservar()).isEqualTo("http://localhost:8080/reservar");
    }
}
