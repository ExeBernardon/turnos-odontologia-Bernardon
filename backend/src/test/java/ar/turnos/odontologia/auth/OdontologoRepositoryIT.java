package ar.turnos.odontologia.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.turnos.odontologia.IntegracionPostgres;

@IntegracionPostgres
class OdontologoRepositoryIT {

    @Autowired
    private OdontologoRepository odontologos;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM odontologo WHERE email LIKE '%@prueba.test'");
    }

    @Test
    void findByEmailEncuentraAlOdontologoGuardado() {
        odontologos.saveAndFlush(new Odontologo("Dra. Prueba", "uno@prueba.test", "hash-ficticio"));

        assertThat(odontologos.findByEmail("uno@prueba.test")).hasValueSatisfying(o -> {
            assertThat(o.getNombre()).isEqualTo("Dra. Prueba");
            assertThat(o.getPasswordHash()).isEqualTo("hash-ficticio");
            assertThat(o.getId()).isNotNull();
        });
    }

    @Test
    void findByEmailDevuelveVacioSiNoExiste() {
        assertThat(odontologos.findByEmail("nadie@prueba.test")).isEmpty();
    }

    @Test
    void emailRepetidoFallaPorOdontologoEmailUk() {
        odontologos.saveAndFlush(new Odontologo("Dra. Uno", "dup@prueba.test", "h1"));

        assertThatThrownBy(() -> odontologos.saveAndFlush(new Odontologo("Dr. Dos", "dup@prueba.test", "h2")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("odontologo_email_uk");
    }
}
