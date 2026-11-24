package ar.turnos.odontologia.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.auth.Odontologo;
import ar.turnos.odontologia.auth.OdontologoRepository;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.horario.HorarioAtencion;
import ar.turnos.odontologia.horario.HorarioAtencionRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;

@IntegracionPostgres
class DataSeederIT {

    @Autowired
    private DataSeeder seeder;

    @Autowired
    private OdontologoRepository odontologos;

    @Autowired
    private PrestacionRepository prestaciones;

    @Autowired
    private HorarioAtencionRepository horarios;

    @Autowired
    private AppProperties app;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    /** Vuelve al estado de arranque: borra todo y deja el seed original. */
    @AfterEach
    void restaurarSeed() throws Exception {
        vaciarTodo();
        seeder.run();
    }

    private void vaciarTodo() {
        jdbc.update("TRUNCATE turno, paciente, bloqueo RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM horario_atencion");
        jdbc.update("DELETE FROM prestacion");
        jdbc.update("DELETE FROM odontologo");
    }

    @Test
    void elArranqueDejaAlOdontologoConHashBcryptQueVerifica() {
        List<Odontologo> todos = odontologos.findAll();

        assertThat(todos).hasSize(1);
        Odontologo o = todos.get(0);
        assertThat(o.getEmail()).isEqualTo(app.seed().odontologo().email());
        assertThat(o.getNombre()).isEqualTo(app.seed().odontologo().nombre());
        assertThat(o.getPasswordHash()).isNotEqualTo(app.seed().odontologo().password());
        assertThat(passwordEncoder.matches(app.seed().odontologo().password(), o.getPasswordHash())).isTrue();
    }

    @Test
    void elArranqueDejaCuatroPrestacionesActivasConSusDuraciones() {
        assertThat(prestaciones.findAll())
                .extracting(Prestacion::getNombre, Prestacion::getDuracionMinutos, Prestacion::isActiva)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Consulta", 30, true),
                        org.assertj.core.groups.Tuple.tuple("Limpieza", 45, true),
                        org.assertj.core.groups.Tuple.tuple("Extracción", 45, true),
                        org.assertj.core.groups.Tuple.tuple("Arreglo / caries", 60, true));
    }

    @Test
    void elArranqueDejaDiezHorariosDeLunesAViernes() {
        assertThat(horarios.findAll()).hasSize(10);
        for (DayOfWeek dia : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            assertThat(horarios.findByDiaSemanaOrderByHoraInicio(dia))
                    .extracting(HorarioAtencion::getHoraInicio, HorarioAtencion::getHoraFin)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(LocalTime.of(9, 0), LocalTime.of(13, 0)),
                            org.assertj.core.groups.Tuple.tuple(LocalTime.of(15, 0), LocalTime.of(19, 0)));
        }
        assertThat(horarios.findByDiaSemanaOrderByHoraInicio(DayOfWeek.SATURDAY)).isEmpty();
        assertThat(horarios.findByDiaSemanaOrderByHoraInicio(DayOfWeek.SUNDAY)).isEmpty();
    }

    @Test
    void noCreaPacientesTurnosNiBloqueos() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo", Integer.class)).isZero();
    }

    @Test
    void correrDeNuevoDejaLosMismosConteos() throws Exception {
        seeder.run();
        seeder.run();

        assertThat(odontologos.count()).isEqualTo(1);
        assertThat(prestaciones.count()).isEqualTo(4);
        assertThat(horarios.count()).isEqualTo(10);
    }

    private DataSeeder seederConOtroOdontologo() {
        AppProperties otro = new AppProperties(app.zonaHoraria(), app.frontendBaseUrl(), app.agenda(),
                app.recordatorio(), app.alerta(), app.cancelacion(), app.turno(), app.consultorio(), app.mail(),
                app.jwt(), new AppProperties.Seed(new AppProperties.Seed.Odontologo("Dr. Otro",
                        "otro@ejemplo.com", "otra-clave-ficticia")));
        return new DataSeeder(odontologos, prestaciones, horarios, otro, passwordEncoder);
    }

    @Test
    void conOtrasVariablesNoCreaNiModificaAlOdontologoExistente() throws Exception {
        String hashAntes = odontologos.findAll().get(0).getPasswordHash();

        seederConOtroOdontologo().run();

        assertThat(odontologos.findAll()).singleElement().satisfies(o -> {
            assertThat(o.getEmail()).isEqualTo(app.seed().odontologo().email());
            assertThat(o.getPasswordHash()).isEqualTo(hashAntes);
        });
        assertThat(odontologos.findByEmail("otro@ejemplo.com")).isEmpty();
    }

    @Test
    void conUnaSolaPrestacionPropiaNoAgregaNinguna() throws Exception {
        jdbc.update("DELETE FROM prestacion WHERE nombre <> 'Consulta'");

        seeder.run();

        assertThat(prestaciones.findAll()).extracting(Prestacion::getNombre).containsExactly("Consulta");
    }

    @Test
    void conHorariosVaciadosVuelveACrearLosDiez() throws Exception {
        jdbc.update("DELETE FROM horario_atencion");

        seeder.run();

        assertThat(horarios.count()).isEqualTo(10);
        assertThat(prestaciones.count()).isEqualTo(4);
        assertThat(odontologos.count()).isEqualTo(1);
    }
}
