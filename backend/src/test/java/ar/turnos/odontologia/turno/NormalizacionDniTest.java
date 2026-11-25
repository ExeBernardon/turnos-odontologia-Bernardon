package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** El DNI se guarda siempre como 7 u 8 digitos, sin puntos (KB 04 / DD-12). */
class NormalizacionDniTest {

    @Test
    void quitaLosPuntos() {
        assertThat(NormalizacionDni.normalizar("99.999.999")).isEqualTo("99999999");
        assertThat(NormalizacionDni.normalizar("9.999.999")).isEqualTo("9999999");
    }

    @Test
    void respetaElDniYaNormalizado() {
        assertThat(NormalizacionDni.normalizar("99999999")).isEqualTo("99999999");
    }

    @Test
    void ignoraEspaciosCircundantes() {
        assertThat(NormalizacionDni.normalizar(" 99.999.999 ")).isEqualTo("99999999");
    }

    @Test
    void rechazaUnDniConCaracteresNoNumericos() {
        assertThatThrownBy(() -> NormalizacionDni.normalizar("99.999.99A"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
