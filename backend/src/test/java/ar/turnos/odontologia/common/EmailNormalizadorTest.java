package ar.turnos.odontologia.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailNormalizadorTest {

    @Test
    void quitaEspaciosYConvierteAMinusculas() {
        assertThat(EmailNormalizador.normalizar("  Odontologo@Ejemplo.com  "))
                .isEqualTo("odontologo@ejemplo.com");
    }

    @Test
    void conservaAcentosConLocaleRoot() {
        assertThat(EmailNormalizador.normalizar("\tODONTÓLOGO@Ejemplo.COM "))
                .isEqualTo("odontólogo@ejemplo.com");
    }

    @Test
    void dejaIgualUnEmailYaNormalizado() {
        assertThat(EmailNormalizador.normalizar("odontologo@ejemplo.com"))
                .isEqualTo("odontologo@ejemplo.com");
    }
}
