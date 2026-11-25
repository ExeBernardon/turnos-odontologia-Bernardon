package ar.turnos.odontologia.disponibilidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Tests unitarios de la aritmetica semiabierta [inicio, fin) usada por el calculo de disponibilidad. */
class IntervaloTest {

    private static final Instant I09 = Instant.parse("2026-12-01T09:00:00Z");
    private static final Instant I10 = Instant.parse("2026-12-01T10:00:00Z");
    private static final Instant I11 = Instant.parse("2026-12-01T11:00:00Z");
    private static final Instant I13 = Instant.parse("2026-12-01T13:00:00Z");

    private static Intervalo iv(Instant inicio, Instant fin) {
        return new Intervalo(inicio, fin);
    }

    @Test
    void intersecaConSolapamientoParcialYConContencion() {
        Intervalo rango = iv(I09, I13);

        assertThat(rango.interseca(iv(I10, I11))).isTrue();
        assertThat(rango.interseca(iv(I09, I10))).isTrue();
        assertThat(iv(I10, I11).interseca(rango)).isTrue();
    }

    @Test
    void bordesSemiabiertosQueSoloSeTocanNoIntersecan() {
        assertThat(iv(I09, I10).interseca(iv(I10, I11))).isFalse();
        assertThat(iv(I10, I11).interseca(iv(I09, I10))).isFalse();
    }

    @Test
    void contieneSoloSiElIntervaloCompletoCabeDentro() {
        Intervalo rango = iv(I09, I13);

        assertThat(rango.contiene(iv(I10, I11))).isTrue();
        assertThat(rango.contiene(iv(I09, I13))).isTrue();
        assertThat(rango.contiene(iv(I08(), I10))).isFalse();
        assertThat(rango.contiene(iv(I12(), I14()))).isFalse();
    }

    @Test
    void restarOcupanteParcialParteElRangoEnDos() {
        assertThat(iv(I09, I13).restar(List.of(iv(I10, I11))))
                .containsExactly(iv(I09, I10), iv(I11, I13));
    }

    @Test
    void restarOcupanteAlPrincipioOAlFinalDejaUnaPieza() {
        assertThat(iv(I09, I13).restar(List.of(iv(I09, I10)))).containsExactly(iv(I10, I13));
        assertThat(iv(I09, I13).restar(List.of(iv(I11, I13)))).containsExactly(iv(I09, I11));
    }

    @Test
    void restarOcupanteQueCubreTodoDaVacioYOcupanteAdyacenteNoAfecta() {
        assertThat(iv(I09, I13).restar(List.of(iv(I09, I13)))).isEmpty();
        assertThat(iv(I09, I13).restar(List.of(iv(I13, I14())))).containsExactly(iv(I09, I13));
        assertThat(iv(I09, I13).restar(List.of(iv(I08(), I09)))).containsExactly(iv(I09, I13));
    }

    @Test
    void restarVariosOcupantesDevuelveLasPiezasLibresOrdenadas() {
        assertThat(iv(I09, I13).restar(List.of(iv(I11, I12()), iv(I10, I11))))
                .containsExactly(iv(I09, I10), iv(I12(), I13));
    }

    private static Instant I08() {
        return I09.minusSeconds(3600);
    }

    private static Instant I12() {
        return I10.plusSeconds(7200);
    }

    private static Instant I14() {
        return I13.plusSeconds(3600);
    }
}
