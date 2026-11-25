package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** Bordes de la regla RN-TUR-11 con reloj fijo: 24 h y 48 h. */
class EstadoInicialTurnoTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-12-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void a24HorasExactasNacePendienteSinFlags() {
        EstadoInicialTurno estado = EstadoInicialTurno.calcular(RELOJ.instant().plus(Duration.ofHours(24)), RELOJ);

        assertThat(estado.estado()).isEqualTo(EstadoTurno.PENDIENTE);
        assertThat(estado.recordatorioEnviado()).isFalse();
        assertThat(estado.alertaPendienteEnviada()).isFalse();
    }

    @Test
    void porDebajoDe24HorasNaceConfirmadoConFlagsYaEnviados() {
        EstadoInicialTurno estado = EstadoInicialTurno.calcular(RELOJ.instant().plus(Duration.ofHours(24))
                .minusNanos(1), RELOJ);

        assertThat(estado.estado()).isEqualTo(EstadoTurno.CONFIRMADO);
        assertThat(estado.recordatorioEnviado()).isTrue();
        assertThat(estado.alertaPendienteEnviada()).isTrue();
    }

    @Test
    void a48HorasSiguePendienteParaElBordeDelAviso() {
        assertThat(EstadoInicialTurno.estado(RELOJ.instant().plus(Duration.ofHours(48)), RELOJ))
                .isEqualTo(EstadoTurno.PENDIENTE);
    }

    @Test
    void lasFranjasDeAvisoIncluyenLosBordesDe24Y48Horas() {
        Instant a48 = RELOJ.instant().plus(Duration.ofHours(48));
        Instant a24 = RELOJ.instant().plus(Duration.ofHours(24));

        assertThat(EstadoInicialTurno.supera48Horas(a48, RELOJ)).isFalse();
        assertThat(EstadoInicialTurno.supera48Horas(a48.plusNanos(1), RELOJ)).isTrue();
        assertThat(EstadoInicialTurno.entre24y48Horas(a48, RELOJ)).isTrue();
        assertThat(EstadoInicialTurno.entre24y48Horas(a24, RELOJ)).isTrue();
        assertThat(EstadoInicialTurno.entre24y48Horas(a24.minusNanos(1), RELOJ)).isFalse();
    }
}
