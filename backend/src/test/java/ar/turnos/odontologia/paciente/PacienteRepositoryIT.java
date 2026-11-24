package ar.turnos.odontologia.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.turnos.odontologia.IntegracionPostgres;

@IntegracionPostgres
class PacienteRepositoryIT {

    @Autowired
    private PacienteRepository pacientes;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE paciente RESTART IDENTITY CASCADE");
    }

    @Test
    void findByDniEncuentraAlPacienteGuardado() {
        pacientes.saveAndFlush(new Paciente("Ana", "Prueba", "99999999"));

        assertThat(pacientes.findByDni("99999999")).hasValueSatisfying(p -> {
            assertThat(p.getNombre()).isEqualTo("Ana");
            assertThat(p.getApellido()).isEqualTo("Prueba");
            assertThat(p.getCreadoEn()).isNotNull();
            assertThat(p.getActualizadoEn()).isNotNull();
        });
    }

    @Test
    void findByDniDevuelveVacioSiNoExiste() {
        pacientes.saveAndFlush(new Paciente("Ana", "Prueba", "99999999"));

        assertThat(pacientes.findByDni("88888888")).isEmpty();
    }

    @Test
    void dniRepetidoFallaPorPacienteDniUk() {
        pacientes.saveAndFlush(new Paciente("Ana", "Prueba", "99999999"));

        assertThatThrownBy(() -> pacientes.saveAndFlush(new Paciente("Beto", "Otro", "99999999")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("paciente_dni_uk");
    }
}
