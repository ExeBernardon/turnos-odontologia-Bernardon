package ar.turnos.odontologia.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;

class ViolacionDeSolapamientoTest {

    private static SQLException sql(String estado) {
        return new SQLException("detalle interno de la base", estado);
    }

    @Test
    void sqlStateExclusionEnvueltoEnDataIntegrityViolationEsSolapamiento() {
        Throwable e = new DataIntegrityViolationException("no se pudo guardar", sql("23P01"));

        assertThat(ViolacionDeSolapamiento.es(e)).isTrue();
    }

    @Test
    void sqlStateExclusionEnvueltoDosNivelesEsSolapamiento() {
        Throwable e = new TransactionSystemException("fallo el commit",
                new RuntimeException("capa intermedia", sql("23P01")));

        assertThat(ViolacionDeSolapamiento.es(e)).isTrue();
    }

    @Test
    void deadlockEntreInsercionesConcurrentesDelMismoHorarioEsSolapamiento() {
        Throwable e = new org.springframework.dao.CannotAcquireLockException("deadlock detected", sql("40P01"));

        assertThat(ViolacionDeSolapamiento.es(e)).isTrue();
    }

    @Test
    void otroFalloDeBloqueoNoEsSolapamiento() {
        Throwable e = new org.springframework.dao.CannotAcquireLockException("lock timeout", sql("55P03"));

        assertThat(ViolacionDeSolapamiento.es(e)).isFalse();
    }

    @Test
    void violacionDeUnicidadNoEsSolapamiento() {
        Throwable e = new DataIntegrityViolationException("duplicado", sql("23505"));

        assertThat(ViolacionDeSolapamiento.es(e)).isFalse();
    }

    @Test
    void sinSqlExceptionEnLaCadenaNoEsSolapamiento() {
        Throwable e = new DataIntegrityViolationException("sin causa SQL", new IllegalStateException("otra cosa"));

        assertThat(ViolacionDeSolapamiento.es(e)).isFalse();
        assertThat(ViolacionDeSolapamiento.es(null)).isFalse();
    }
}
