package ar.turnos.odontologia.notificacion;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disparador periodico de los avisos programados: solo programa, la logica vive en
 * {@link AvisosProgramadosService}. {@code fixedDelay} cuenta desde el fin del ciclo anterior (dos ciclos
 * nunca se solapan, aunque el SMTP cuelgue) y no hay {@code initialDelay}: al arrancar corre un ciclo para
 * ponerse al dia. El perfil de test lo desactiva con {@code app.recordatorio.job-habilitado=false}.
 */
@Component
@ConditionalOnProperty(name = "app.recordatorio.job-habilitado", havingValue = "true", matchIfMissing = true)
public class RecordatorioJob {

    private final AvisosProgramadosService servicio;

    public RecordatorioJob(AvisosProgramadosService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(fixedDelayString = "${app.recordatorio.intervalo-min}", timeUnit = TimeUnit.MINUTES)
    public void ejecutar() {
        servicio.ejecutarCiclo();
    }
}
