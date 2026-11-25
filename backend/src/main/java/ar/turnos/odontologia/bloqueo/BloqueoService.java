package ar.turnos.odontologia.bloqueo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import ar.turnos.odontologia.common.ApiException;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.notificacion.NotificacionTurnos;
import ar.turnos.odontologia.turno.CanceladoPor;
import ar.turnos.odontologia.turno.EstadoTurno;
import ar.turnos.odontologia.turno.Turno;
import ar.turnos.odontologia.turno.TurnoRepository;

@Service
@Transactional(readOnly = true)
public class BloqueoService {

    private static final Logger log = LoggerFactory.getLogger(BloqueoService.class);

    static final int MAX_DIAS_LISTADO = 31;
    static final String MSG_NO_ENCONTRADO = "No encontramos el recurso solicitado.";
    static final String MSG_RANGO_INVALIDO = "La hora de inicio debe ser anterior a la de fin.";
    static final String MSG_VALIDACION = "Hay campos con errores.";

    private final BloqueoRepository bloqueos;
    private final TurnoRepository turnos;
    private final NotificacionTurnos notificaciones;
    private final BloqueoMapper mapper;
    private final ZoneId zona;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;

    public BloqueoService(BloqueoRepository bloqueos, TurnoRepository turnos, NotificacionTurnos notificaciones,
            BloqueoMapper mapper, AppProperties app, PlatformTransactionManager tm) {
        this.bloqueos = bloqueos;
        this.turnos = turnos;
        this.notificaciones = notificaciones;
        this.mapper = mapper;
        this.zona = app.zonaHoraria();
        this.escritura = new TransactionTemplate(tm);
        this.lectura = new TransactionTemplate(tm);
        this.lectura.setReadOnly(true);
    }

    public List<BloqueoResponse> listar(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)
                || ChronoUnit.DAYS.between(desde, hasta) + 1 > MAX_DIAS_LISTADO) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", MSG_VALIDACION);
        }
        Instant ini = desde.atStartOfDay(zona).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(zona).toInstant();
        return bloqueos.findQueIntersecan(ini, fin).stream().map(mapper::aBloqueo).toList();
    }

    public BloqueoPrevisualizacionResponse previsualizar(BloqueoRequest req) {
        validarRango(req.inicio(), req.fin());
        return new BloqueoPrevisualizacionResponse(
                mapper.aTurnos(turnos.findActivosQueIntersecan(req.inicio(), req.fin())));
    }

    @Transactional
    public void eliminar(Long id) {
        bloqueos.delete(bloqueos.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", MSG_NO_ENCONTRADO)));
    }

    /**
     * Crea el bloqueo y, si se confirmo, cancela los turnos afectados en una sola transaccion. Los avisos salen
     * despues del commit (RN-BLO-04) y un fallo de aviso nunca revierte ni cambia la respuesta (RN-NOT-05).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BloqueoCreadoResponse crear(BloqueoCrearRequest req) {
        validarRango(req.inicio(), req.fin());

        Creacion creacion = escritura.execute(status -> {
            List<Turno> afectados = turnos.findActivosQueIntersecan(req.inicio(), req.fin());
            if (!afectados.isEmpty() && !req.cancelar()) {
                throw new BloqueoConTurnosAfectadosException(mapper.aTurnos(afectados));
            }
            Bloqueo bloqueo = bloqueos.saveAndFlush(new Bloqueo(req.inicio(), req.fin(), req.motivo()));
            afectados.forEach(t -> {
                t.setEstado(EstadoTurno.CANCELADO);
                t.setCanceladoPor(CanceladoPor.ODONTOLOGO);
            });
            if (!afectados.isEmpty()) {
                turnos.saveAllAndFlush(afectados);
            }
            return new Creacion(new BloqueoCreadoResponse(mapper.aBloqueo(bloqueo), mapper.aTurnos(afectados)),
                    afectados.stream().map(Turno::getId).toList());
        });

        avisar(creacion.idsCancelados());
        return creacion.respuesta();
    }

    /** Segunda transaccion de lectura: el puerto puede leer paciente y prestacion (LAZY) del turno ya confirmado. */
    private void avisar(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        lectura.executeWithoutResult(status -> turnos.findAllById(ids).forEach(t -> {
            try {
                if (!notificaciones.cancelacionPorBloqueo(t)) {
                    log.warn("No se pudo avisar la cancelacion por bloqueo del turno {}", t.getId());
                }
            } catch (RuntimeException e) {
                log.warn("Fallo el aviso de cancelacion por bloqueo del turno {}: {}", t.getId(),
                        e.getClass().getSimpleName());
            }
        }));
    }

    private static void validarRango(Instant inicio, Instant fin) {
        if (!inicio.isBefore(fin)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "RANGO_HORARIO_INVALIDO", MSG_RANGO_INVALIDO);
        }
    }

    private record Creacion(BloqueoCreadoResponse respuesta, List<Long> idsCancelados) {
    }
}
