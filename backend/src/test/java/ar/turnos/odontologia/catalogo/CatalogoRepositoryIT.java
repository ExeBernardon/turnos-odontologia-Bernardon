package ar.turnos.odontologia.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.horario.HorarioAtencion;
import ar.turnos.odontologia.horario.HorarioAtencionRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;

@IntegracionPostgres
class CatalogoRepositoryIT {

    @Autowired
    private PrestacionRepository prestaciones;

    @Autowired
    private HorarioAtencionRepository horarios;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        // El seed carga lunes a viernes: estos tests usan solo sabado y domingo y borran lo propio.
        jdbc.update("DELETE FROM horario_atencion WHERE dia_semana IN (6, 7)");
        jdbc.update("DELETE FROM prestacion WHERE nombre LIKE 'Prueba %'");
    }

    @Test
    void prestacionNuevaQuedaActivaPorDefectoYSeLee() {
        Prestacion guardada = prestaciones.saveAndFlush(new Prestacion("Prueba blanqueamiento", 90));

        Prestacion leida = prestaciones.findById(guardada.getId()).orElseThrow();

        assertThat(leida.getNombre()).isEqualTo("Prueba blanqueamiento");
        assertThat(leida.getDuracionMinutos()).isEqualTo(90);
        assertThat(leida.isActiva()).isTrue();
    }

    @Test
    void prestacionDesactivadaConservaElFlag() {
        Prestacion p = new Prestacion("Prueba ortodoncia", 30);
        p.setActiva(false);
        Prestacion guardada = prestaciones.saveAndFlush(p);

        assertThat(prestaciones.findById(guardada.getId()).orElseThrow().isActiva()).isFalse();
    }

    @Test
    void horarioSeGuardaConHoraTimeYDiaSmallint() {
        HorarioAtencion h = horarios.saveAndFlush(
                new HorarioAtencion(DayOfWeek.SUNDAY, LocalTime.of(9, 0), LocalTime.of(13, 0)));

        Short dia = jdbc.queryForObject("SELECT dia_semana FROM horario_atencion WHERE id = ?", Short.class, h.getId());
        String tipoDia = jdbc.queryForObject("SELECT data_type FROM information_schema.columns "
                + "WHERE table_name = 'horario_atencion' AND column_name = 'dia_semana'", String.class);
        HorarioAtencion leido = horarios.findById(h.getId()).orElseThrow();

        assertThat(dia).isEqualTo((short) 7);
        assertThat(tipoDia).isEqualTo("smallint");
        assertThat(leido.getDiaSemana()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(leido.getHoraInicio()).isEqualTo(LocalTime.of(9, 0));
        assertThat(leido.getHoraFin()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void horariosDelDiaVienenOrdenadosYSinLosDeOtroDia() {
        horarios.saveAndFlush(new HorarioAtencion(DayOfWeek.SATURDAY, LocalTime.of(15, 0), LocalTime.of(19, 0)));
        horarios.saveAndFlush(new HorarioAtencion(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(13, 0)));
        horarios.saveAndFlush(new HorarioAtencion(DayOfWeek.SUNDAY, LocalTime.of(10, 0), LocalTime.of(12, 0)));

        List<HorarioAtencion> sabado = horarios.findByDiaSemanaOrderByHoraInicio(DayOfWeek.SATURDAY);

        assertThat(sabado).extracting(HorarioAtencion::getHoraInicio)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(15, 0));
        assertThat(sabado).extracting(HorarioAtencion::getDiaSemana).containsOnly(DayOfWeek.SATURDAY);
    }
}
