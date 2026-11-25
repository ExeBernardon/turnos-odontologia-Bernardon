package ar.turnos.odontologia.seed;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ar.turnos.odontologia.auth.Odontologo;
import ar.turnos.odontologia.auth.OdontologoRepository;
import ar.turnos.odontologia.common.EmailNormalizador;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.horario.HorarioAtencion;
import ar.turnos.odontologia.horario.HorarioAtencionRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;

/**
 * Carga inicial idempotente con datos ficticios (KB 04, "Seed"). Cada paso tiene su propia guarda
 * {@code count() == 0}, asi que reiniciar no duplica nada ni pisa lo que edito el odontologo.
 * Solo registra cantidades: nunca el email, el nombre ni la contrasena.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final List<DayOfWeek> DIAS_LABORABLES = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private final OdontologoRepository odontologos;
    private final PrestacionRepository prestaciones;
    private final HorarioAtencionRepository horarios;
    private final AppProperties app;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(OdontologoRepository odontologos, PrestacionRepository prestaciones,
            HorarioAtencionRepository horarios, AppProperties app, PasswordEncoder passwordEncoder) {
        this.odontologos = odontologos;
        this.prestaciones = prestaciones;
        this.horarios = horarios;
        this.app = app;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int odontologosCreados = sembrarOdontologo();
        int prestacionesCreadas = sembrarPrestaciones();
        int horariosCreados = sembrarHorarios();
        log.info("Carga inicial: {} odontologo(s), {} prestacion(es) y {} horario(s) creados",
                odontologosCreados, prestacionesCreadas, horariosCreados);
    }

    private int sembrarOdontologo() {
        if (odontologos.count() > 0) {
            return 0;
        }
        AppProperties.Seed.Odontologo datos = app.seed().odontologo();
        odontologos.save(new Odontologo(datos.nombre(), EmailNormalizador.normalizar(datos.email()),
                passwordEncoder.encode(datos.password())));
        return 1;
    }

    private int sembrarPrestaciones() {
        if (prestaciones.count() > 0) {
            return 0;
        }
        List<Prestacion> iniciales = List.of(
                new Prestacion("Consulta", 30),
                new Prestacion("Limpieza", 45),
                new Prestacion("Extracción", 45),
                new Prestacion("Arreglo / caries", 60));
        prestaciones.saveAll(iniciales);
        return iniciales.size();
    }

    private int sembrarHorarios() {
        if (horarios.count() > 0) {
            return 0;
        }
        List<HorarioAtencion> iniciales = DIAS_LABORABLES.stream()
                .flatMap(dia -> java.util.stream.Stream.of(
                        new HorarioAtencion(dia, LocalTime.of(9, 0), LocalTime.of(13, 0)),
                        new HorarioAtencion(dia, LocalTime.of(15, 0), LocalTime.of(19, 0))))
                .toList();
        horarios.saveAll(iniciales);
        return iniciales.size();
    }
}
