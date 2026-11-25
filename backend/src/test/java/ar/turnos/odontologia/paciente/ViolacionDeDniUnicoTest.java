package ar.turnos.odontologia.paciente;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ViolacionDeDniUnicoTest {

    @Test
    void detectaLaConstraintEnElMensajeDeUnaCausaProfunda() {
        Throwable e = new DataIntegrityViolationException("no se pudo ejecutar",
                new RuntimeException("wrapper", new SQLException(
                        "ERROR: duplicate key value violates unique constraint \"paciente_dni_uk\"", "23505")));

        assertThat(ViolacionDeDniUnico.es(e)).isTrue();
    }

    @Test
    void detectaLaConstraintEnElMensajeDelPropioErrorExterno() {
        assertThat(ViolacionDeDniUnico.es(
                new DataIntegrityViolationException("violates unique constraint \"paciente_dni_uk\""))).isTrue();
    }

    @Test
    void otraConstraintNoEsViolacionDeDni() {
        Throwable e = new DataIntegrityViolationException("x",
                new SQLException("duplicate key value violates unique constraint \"turno_token_uk\"", "23505"));

        assertThat(ViolacionDeDniUnico.es(e)).isFalse();
    }

    @Test
    void unaCadenaSinMensajeNoRompe() {
        assertThat(ViolacionDeDniUnico.es(new RuntimeException((String) null))).isFalse();
        assertThat(ViolacionDeDniUnico.es(null)).isFalse();
    }
}
