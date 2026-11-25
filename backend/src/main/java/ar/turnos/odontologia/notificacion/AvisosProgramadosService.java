package ar.turnos.odontologia.notificacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.turno.EstadoTurno;
import ar.turnos.odontologia.turno.Turno;

/**
 * Ciclo de avisos que el sistema emite solo por el paso del tiempo (C-12): el Recordatorio al paciente
 * (turno PENDIENTE a 48 h o menos) y la alerta "Turno no confirmado" al odontologo (PENDIENTE a menos de
 * 24 h). Cada turno se procesa en su propia transaccion: se relee, se envia y, solo si el envio salio,
 * se marca el flag con un UPDATE condicionado que no pisa cambios concurrentes del turno.
 */
@Service
public class AvisosProgramadosService {

    private static final Logger log = LoggerFactory.getLogger(AvisosProgramadosService.class);

    private static final String TAREA_RECORDATORIO = "RECORDATORIO";
    private static final String TAREA_ALERTA = "TURNO_NO_CONFIRMADO";

    private final TurnoAvisosRepository avisos;
    private final NotificacionTurnos notificaciones;
    private final AppProperties app;
    private final Clock clock;
    private final TransactionTemplate transaccion;

    public AvisosProgramadosService(TurnoAvisosRepository avisos, NotificacionTurnos notificaciones,
            AppProperties app, Clock clock, PlatformTransactionManager transacciones) {
        this.avisos = avisos;
        this.notificaciones = notificaciones;
        this.app = app;
        this.clock = clock;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /** Toma "ahora" una sola vez del reloj y corre el ciclo completo. */
    public ResultadoCiclo ejecutarCiclo() {
        return ejecutarCiclo(clock.instant());
    }

    /** Recordatorios primero y alertas despues, ambos con el mismo {@code ahora}. */
    public ResultadoCiclo ejecutarCiclo(Instant ahora) {
        Instant hastaRecordatorio = ahora.plus(Duration.ofHours(app.recordatorio().horasAntes()));
        Instant hastaAlerta = ahora.plus(Duration.ofHours(app.alerta().pendienteHoras()));

        int[] recordatorios = tarea(TAREA_RECORDATORIO, avisos.idsParaRecordatorio(ahora, hastaRecordatorio),
                turno -> turno.getEstado() == EstadoTurno.PENDIENTE && !turno.isRecordatorioEnviado()
                        && turno.getInicio().isAfter(ahora) && !turno.getInicio().isAfter(hastaRecordatorio),
                notificaciones::recordatorio, avisos::marcarRecordatorioEnviado);
        int[] alertas = tarea(TAREA_ALERTA, avisos.idsParaAlerta(ahora, hastaAlerta),
                turno -> turno.getEstado() == EstadoTurno.PENDIENTE && !turno.isAlertaPendienteEnviada()
                        && turno.getInicio().isAfter(ahora) && turno.getInicio().isBefore(hastaAlerta),
                notificaciones::turnoNoConfirmado, avisos::marcarAlertaEnviada);

        ResultadoCiclo resultado = new ResultadoCiclo(recordatorios[0], recordatorios[1], alertas[0], alertas[1]);
        if (resultado.huboActividad()) {
            log.info("Ciclo de avisos: recordatoriosEnviados={} recordatoriosFallidos={} alertasEnviadas={} "
                    + "alertasFallidas={}", resultado.recordatoriosEnviados(), resultado.recordatoriosFallidos(),
                    resultado.alertasEnviadas(), resultado.alertasFallidas());
        }
        return resultado;
    }

    /** Devuelve {enviados, fallidos}. Un fallo en un turno se registra y no frena a los demas. */
    private int[] tarea(String nombre, List<Long> ids, Predicate<Turno> sigueElegible,
            Predicate<Turno> envio, BiFunction<Long, Instant, Integer> marcar) {
        int enviados = 0;
        int fallidos = 0;
        for (Long id : ids) {
            try {
                Boolean salio = transaccion.execute(estado -> procesar(id, sigueElegible, envio, marcar));
                if (salio == null) {
                    continue;
                }
                if (salio) {
                    enviados++;
                } else {
                    fallidos++;
                }
            } catch (RuntimeException e) {
                fallidos++;
                // Sin mensaje ni stack: un rechazo SMTP puede incluir la direccion del destinatario.
                log.warn("Fallo el aviso programado: turnoId={} tarea={} error={}", id, nombre,
                        e.getClass().getSimpleName());
            }
        }
        return new int[] { enviados, fallidos };
    }

    /** {@code null} si el turno ya no es elegible; si no, si el aviso salio. */
    private Boolean procesar(Long id, Predicate<Turno> sigueElegible, Predicate<Turno> envio,
            BiFunction<Long, Instant, Integer> marcar) {
        Optional<Turno> releido = avisos.findById(id).filter(sigueElegible);
        if (releido.isEmpty()) {
            return null;
        }
        Turno turno = releido.get();
        boolean salio = envio.test(turno);
        if (salio) {
            marcar.apply(id, turno.getInicio());
        }
        return salio;
    }
}
