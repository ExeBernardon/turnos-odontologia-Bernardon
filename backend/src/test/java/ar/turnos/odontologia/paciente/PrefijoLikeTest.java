package ar.turnos.odontologia.paciente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PrefijoLikeTest {

    @Test
    void escapaLosComodinesYLaBarra() {
        assertThat(PrefijoLike.escapar("100%")).isEqualTo("100\\%");
        assertThat(PrefijoLike.escapar("a_b")).isEqualTo("a\\_b");
        assertThat(PrefijoLike.escapar("a\\b")).isEqualTo("a\\\\b");
    }

    @Test
    void dejaIntactoUnTextoSinCaracteresEspeciales() {
        assertThat(PrefijoLike.escapar("Ejemplo")).isEqualTo("Ejemplo");
        assertThat(PrefijoLike.escapar("9999")).isEqualTo("9999");
    }
}
