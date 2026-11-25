package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ar.turnos.odontologia.common.ApiException;

/** Plazos de confirmación (48 h) y cancelación (24 h) con reloj fijo y bordes inclusivos. */
class PlazosTurnoTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-12-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void confirmacionCon48hExactasOPeriodMenorEstaDentroDelPlazo() {
        assertThat(PlazosTurno.confirmablePorPaciente(en(48), RELOJ)).isTrue();
        assertThat(PlazosTurno.confirmablePorPaciente(en(47), RELOJ)).isTrue();
    }

    @Test
    void confirmacionConMasDe48hOPasadoEstaFueraDelPlazo() {
        assertThat(PlazosTurno.confirmablePorPaciente(en(49), RELOJ)).isFalse();
        assertThat(PlazosTurno.confirmablePorPaciente(en(-1), RELOJ)).isFalse();
        assertThat(PlazosTurno.confirmablePorPaciente(RELOJ.instant(), RELOJ)).isFalse();
    }

    @Test
    void cancelacionCon24hExactasOMasEstaDentroDelPlazo() {
        assertThat(PlazosTurno.cancelablePorPaciente(en(24), RELOJ)).isTrue();
        assertThat(PlazosTurno.cancelablePorPaciente(en(25), RELOJ)).isTrue();
    }

    @Test
    void cancelacionConMenosDe24hEstaFueraDelPlazo() {
        assertThat(PlazosTurno.cancelablePorPaciente(en(23), RELOJ)).isFalse();
        assertThat(PlazosTurno.cancelablePorPaciente(en(-1), RELOJ)).isFalse();
    }

    @Test
    void exigirConfirmacionFueraDePlazoUsaCodigoYMensajeDelContrato() {
        assertThatCode(() -> PlazosTurno.exigirConfirmable(en(48), RELOJ)).doesNotThrowAnyException();

        assertThatThrownBy(() -> PlazosTurno.exigirConfirmable(en(49), RELOJ))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getCodigo()).isEqualTo("CONFIRMACION_FUERA_DE_PLAZO");
                    assertThat(e.getMessage()).isEqualTo(
                            "Todavía no se puede confirmar el turno. Podés confirmarlo desde 48 h antes.");
                });
    }

    @Test
    void exigirCancelacionFueraDePlazoUsaCodigoYMensajeDelContrato() {
        assertThatCode(() -> PlazosTurno.exigirCancelable(en(24), RELOJ)).doesNotThrowAnyException();

        assertThatThrownBy(() -> PlazosTurno.exigirCancelable(en(23), RELOJ))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getCodigo()).isEqualTo("CANCELACION_FUERA_DE_PLAZO");
                    assertThat(e.getMessage()).isEqualTo(
                            "Ya no se puede cancelar desde el link (faltan menos de 24 h). Comunicate con el consultorio.");
                });
    }

    private static Instant en(long horas) {
        return RELOJ.instant().plus(Duration.ofHours(horas));
    }
}
