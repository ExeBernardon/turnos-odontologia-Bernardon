package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import ar.turnos.odontologia.config.SchedulingConfig;

/** Cableado del {@code @Scheduled}: contexto minimo de Spring, sin base de datos. */
class RecordatorioJobTest {

    @Test
    void ejecutarDelegaEnElCicloDelServicio() {
        AvisosProgramadosService servicio = mock(AvisosProgramadosService.class);

        new RecordatorioJob(servicio).ejecutar();

        verify(servicio).ejecutarCiclo();
    }

    @Nested
    @SpringJUnitConfig({ SchedulingConfig.class, RecordatorioJob.class })
    @TestPropertySource(properties = "app.recordatorio.intervalo-min=15")
    class ConIntervaloPorDefecto {

        @Autowired
        ScheduledTaskHolder tareas;
        @MockitoBean
        AvisosProgramadosService servicio;

        @Test
        void registraUnaTareaFixedDelayDe15Minutos() {
            assertThat(delaysRegistrados(tareas)).containsExactly(Duration.ofMinutes(15));
        }
    }

    @Nested
    @SpringJUnitConfig({ SchedulingConfig.class, RecordatorioJob.class })
    @TestPropertySource(properties = "app.recordatorio.intervalo-min=5")
    class ConIntervaloDe5Minutos {

        @Autowired
        ScheduledTaskHolder tareas;
        @MockitoBean
        AvisosProgramadosService servicio;

        @Test
        void registraUnaTareaFixedDelayDe5Minutos() {
            assertThat(delaysRegistrados(tareas)).containsExactly(Duration.ofMinutes(5));
        }
    }

    @Nested
    @SpringJUnitConfig({ SchedulingConfig.class, RecordatorioJob.class })
    @TestPropertySource(properties = { "app.recordatorio.intervalo-min=15",
            "app.recordatorio.job-habilitado=false" })
    class Deshabilitado {

        @Autowired
        ScheduledTaskHolder tareas;
        @MockitoBean
        AvisosProgramadosService servicio;

        @Test
        void conLaPropiedadEnFalseNoSeRegistraNingunaTarea() {
            assertThat(tareas.getScheduledTasks()).isEmpty();
        }
    }

    private static List<Duration> delaysRegistrados(ScheduledTaskHolder tareas) {
        return tareas.getScheduledTasks().stream()
                .map(t -> ((FixedDelayTask) t.getTask()).getIntervalDuration())
                .toList();
    }
}
