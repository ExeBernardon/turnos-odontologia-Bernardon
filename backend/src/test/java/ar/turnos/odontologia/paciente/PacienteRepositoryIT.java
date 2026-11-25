package ar.turnos.odontologia.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;
import ar.turnos.odontologia.turno.EstadoTurno;
import ar.turnos.odontologia.turno.Turno;
import ar.turnos.odontologia.turno.TurnoRepository;

@IntegracionPostgres
class PacienteRepositoryIT {

    private static final String PRESTACION = "Prueba paciente";

    @Autowired
    private PacienteRepository pacientes;

    @Autowired
    private PrestacionRepository prestaciones;

    @Autowired
    private TurnoRepository turnos;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre = ?", PRESTACION);
    }

    private static Instant hora(int h) {
        return OffsetDateTime.parse("2026-11-25T00:00:00-03:00").plusHours(h).toInstant();
    }

    /** Crea un turno de 30 min a la hora indicada y fuerza su {@code creado_en} (ISO, UTC). */
    private Turno turno(Paciente p, int horaInicio, EstadoTurno estado, String email, String telefono,
            String creadoEn) {
        Prestacion prestacion = prestaciones.findAll().stream()
                .filter(x -> x.getNombre().equals(PRESTACION)).findFirst()
                .orElseGet(() -> prestaciones.saveAndFlush(new Prestacion(PRESTACION, 30)));
        Turno t = turnos.saveAndFlush(new Turno(p, prestacion, hora(horaInicio), hora(horaInicio).plusSeconds(1800),
                estado, "tok-" + p.getId() + "-" + horaInicio, email, telefono));
        jdbc.update("UPDATE turno SET creado_en = ?::timestamptz WHERE id = ?", creadoEn, t.getId());
        return t;
    }

    private Map<Long, ContactoUltimoTurno> contactos(Long... ids) {
        return pacientes.contactosDelUltimoTurno(List.of(ids)).stream()
                .collect(Collectors.toMap(ContactoUltimoTurno::getPacienteId, Function.identity()));
    }

    private void tresPacientes() {
        pacientes.saveAndFlush(new Paciente("Beto", "Muestra", "88888888"));
        pacientes.saveAndFlush(new Paciente("Ana", "Ejemplo", "99999999"));
        pacientes.saveAndFlush(new Paciente("Carla", "Ejemplo", "99999997"));
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

    // ---- busqueda (task 2.1) ----

    @Test
    void buscaPorPrefijoDeDni() {
        tresPacientes();

        assertThat(pacientes.buscarPorPrefijoDni("9999")).extracting(Paciente::getDni)
                .containsExactly("99999999", "99999997");
        assertThat(pacientes.buscarPorPrefijoDni("88")).extracting(Paciente::getDni).containsExactly("88888888");
        assertThat(pacientes.buscarPorPrefijoDni("77")).isEmpty();
    }

    @Test
    void buscaPorPrefijoDeApellidoSinDistinguirMayusculas() {
        tresPacientes();

        assertThat(pacientes.buscarPorPrefijoApellido("ejem")).extracting(Paciente::getApellido)
                .containsExactly("Ejemplo", "Ejemplo");
        assertThat(pacientes.buscarPorPrefijoApellido("MUES")).extracting(Paciente::getApellido)
                .containsExactly("Muestra");
        assertThat(pacientes.buscarPorPrefijoApellido("jemplo")).isEmpty();
    }

    @Test
    void losComodinesDeLikeSeTratanComoTexto() {
        tresPacientes();
        pacientes.saveAndFlush(new Paciente("Dora", "%Raro", "77777777"));
        pacientes.saveAndFlush(new Paciente("Eva", "_Raro", "77777776"));

        assertThat(pacientes.buscarPorPrefijoApellido("%")).extracting(Paciente::getApellido)
                .containsExactly("%Raro");
        assertThat(pacientes.buscarPorPrefijoApellido("_")).extracting(Paciente::getApellido)
                .containsExactly("_Raro");
        assertThat(pacientes.buscarPorPrefijoApellido("\\")).isEmpty();
    }

    @Test
    void ordenaPorApellidoNombreEId() {
        tresPacientes();

        assertThat(pacientes.findAllByOrderByApellidoAscNombreAscIdAsc()).extracting(Paciente::getNombre)
                .containsExactly("Ana", "Carla", "Beto");
        assertThat(pacientes.buscarPorPrefijoDni("99")).extracting(Paciente::getNombre)
                .containsExactly("Ana", "Carla");
    }

    @Test
    void existeDniExcluyendoAlPropioPaciente() {
        Paciente ana = pacientes.saveAndFlush(new Paciente("Ana", "Ejemplo", "99999999"));
        Paciente beto = pacientes.saveAndFlush(new Paciente("Beto", "Muestra", "88888888"));

        assertThat(pacientes.existsByDniAndIdNot("99999999", ana.getId())).isFalse();
        assertThat(pacientes.existsByDniAndIdNot("99999999", beto.getId())).isTrue();
        assertThat(pacientes.existsByDni("99999999")).isTrue();
        assertThat(pacientes.existsByDni("11111111")).isFalse();
    }

    // ---- contacto del ultimo turno (task 2.2) ----

    @Test
    void ultimoContactoEsElDelTurnoCreadoMasRecienteAunqueOtroTengaInicioPosterior() {
        Paciente ana = pacientes.saveAndFlush(new Paciente("Ana", "Ejemplo", "99999999"));
        turno(ana, 9, EstadoTurno.CONFIRMADO, "a1@ejemplo.com", "111", "2026-11-01T10:00:00Z");
        turno(ana, 15, EstadoTurno.PENDIENTE, "a2@ejemplo.com", "222", "2026-11-02T10:00:00Z");
        turno(ana, 11, EstadoTurno.PENDIENTE, "a3@ejemplo.com", "333", "2026-11-03T10:00:00Z");

        ContactoUltimoTurno c = contactos(ana.getId()).get(ana.getId());

        assertThat(c.getEmail()).isEqualTo("a3@ejemplo.com");
        assertThat(c.getTelefono()).isEqualTo("333");
    }

    @Test
    void elUltimoTurnoCanceladoTambienCuenta() {
        Paciente ana = pacientes.saveAndFlush(new Paciente("Ana", "Ejemplo", "99999999"));
        turno(ana, 9, EstadoTurno.CONFIRMADO, "viejo@ejemplo.com", "111", "2026-11-01T10:00:00Z");
        turno(ana, 11, EstadoTurno.CANCELADO, "cancelado@ejemplo.com", "222", "2026-11-05T10:00:00Z");

        assertThat(contactos(ana.getId()).get(ana.getId()).getEmail()).isEqualTo("cancelado@ejemplo.com");
    }

    @Test
    void conIgualCreadoEnDesempataElIdMayor() {
        Paciente ana = pacientes.saveAndFlush(new Paciente("Ana", "Ejemplo", "99999999"));
        turno(ana, 9, EstadoTurno.CONFIRMADO, "primero@ejemplo.com", "111", "2026-11-01T10:00:00Z");
        turno(ana, 11, EstadoTurno.CONFIRMADO, "segundo@ejemplo.com", "222", "2026-11-01T10:00:00Z");

        assertThat(contactos(ana.getId()).get(ana.getId()).getEmail()).isEqualTo("segundo@ejemplo.com");
    }

    @Test
    void pacienteSinTurnosNoApareceYLosContactosNoSeMezclan() {
        Paciente ana = pacientes.saveAndFlush(new Paciente("Ana", "Ejemplo", "99999999"));
        Paciente beto = pacientes.saveAndFlush(new Paciente("Beto", "Muestra", "88888888"));
        Paciente carla = pacientes.saveAndFlush(new Paciente("Carla", "Otra", "77777777"));
        turno(ana, 9, EstadoTurno.CONFIRMADO, "ana@ejemplo.com", "111", "2026-11-01T10:00:00Z");
        turno(beto, 11, EstadoTurno.PENDIENTE, "beto@ejemplo.com", "222", "2026-11-02T10:00:00Z");

        Map<Long, ContactoUltimoTurno> r = contactos(ana.getId(), beto.getId(), carla.getId());

        assertThat(r).containsOnlyKeys(ana.getId(), beto.getId());
        assertThat(r.get(ana.getId()).getEmail()).isEqualTo("ana@ejemplo.com");
        assertThat(r.get(beto.getId()).getEmail()).isEqualTo("beto@ejemplo.com");
    }
}
