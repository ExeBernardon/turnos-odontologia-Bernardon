package ar.turnos.odontologia.turno;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.turnos.odontologia.common.ApiException;
import ar.turnos.odontologia.common.DetalleError;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.disponibilidad.ContextoValidacion;
import ar.turnos.odontologia.disponibilidad.DisponibilidadService;
import ar.turnos.odontologia.disponibilidad.ModoDisponibilidad;
import ar.turnos.odontologia.notificacion.NotificacionTurnos;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.paciente.PacienteRepository;
import ar.turnos.odontologia.paciente.PacienteRequest;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;

/**
 * Alta de turnos compartida por la reserva publica y la carga manual: valida el horario,
 * resuelve el paciente por DNI, persiste con {@code saveAndFlush} (la restriccion de
 * exclusion de la base es la garantia final contra solapamientos) y pide los avisos.
 */
@Service
@Transactional
public class TurnoService {

    private static final String MSG_CONSENTIMIENTO =
            "Tenés que aceptar el tratamiento de tus datos para reservar.";
    private static final String MSG_NO_ENCONTRADO = "No encontramos el recurso solicitado.";
    private static final String MSG_DNI_DUPLICADO = "Ya existe un paciente con ese DNI.";
    private static final String MSG_TOKEN_INVALIDO = "El link no es válido.";
    private static final String MSG_VALIDACION = "Hay campos con errores.";
    private static final int MAX_DIAS_AGENDA = 31;

    private final TurnoRepository turnos;
    private final PacienteRepository pacientes;
    private final PrestacionRepository prestaciones;
    private final DisponibilidadService disponibilidad;
    private final NotificacionTurnos notificaciones;
    private final GeneradorToken generadorToken;
    private final TurnoMapper mapper;
    private final Clock clock;
    private final ZoneId zona;

    public TurnoService(TurnoRepository turnos, PacienteRepository pacientes,
            PrestacionRepository prestaciones, DisponibilidadService disponibilidad,
            NotificacionTurnos notificaciones, GeneradorToken generadorToken,
            TurnoMapper mapper, Clock clock, AppProperties app) {
        this.turnos = turnos;
        this.pacientes = pacientes;
        this.prestaciones = prestaciones;
        this.disponibilidad = disponibilidad;
        this.notificaciones = notificaciones;
        this.generadorToken = generadorToken;
        this.mapper = mapper;
        this.clock = clock;
        this.zona = app.zonaHoraria();
    }

    public TurnoPublicoResponse reservar(ReservaPublicaRequest request) {
        if (!Boolean.TRUE.equals(request.consentimiento())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "CONSENTIMIENTO_REQUERIDO", MSG_CONSENTIMIENTO);
        }
        Instant inicio = request.inicio().toInstant();
        Prestacion prestacion = prestacion(request.prestacionId());
        disponibilidad.validarHorario(prestacion, inicio, ContextoValidacion.publica());

        Paciente paciente = resolverPacientePublico(request);
        Turno turno = construir(paciente, prestacion, inicio, request.email(), request.telefono(),
                clock.instant());
        Turno guardado = turnos.saveAndFlush(turno);
        avisar(guardado);
        return mapper.aPublico(guardado);
    }

    public TurnoResponse crearManual(TurnoManualRequest request) {
        Instant inicio = request.inicio().toInstant();
        Prestacion prestacion = prestacion(request.prestacionId());
        disponibilidad.validarHorario(prestacion, inicio, ContextoValidacion.odontologo());

        Paciente paciente = resolverPacienteManual(request);
        Turno turno = construir(paciente, prestacion, inicio, request.emailContacto(),
                request.telefonoContacto(), null);
        Turno guardado = turnos.saveAndFlush(turno);
        avisar(guardado);
        return mapper.aAgenda(guardado);
    }

    public TurnoPublicoResponse consultarPublico(String token) {
        return mapper.aPublico(porToken(token));
    }

    // ---- Acciones del paciente por token (C-08) ----

    /** Orden: token → idempotencia → transición → plazo. La confirmación no envía mails. */
    public TurnoPublicoResponse confirmarPublico(String token) {
        Turno turno = porToken(token);
        if (!TransicionesTurno.confirmar(turno, clock)) {
            return mapper.aPublico(turno);
        }
        PlazosTurno.exigirConfirmable(turno.getInicio(), clock);
        turno.setEstado(EstadoTurno.CONFIRMADO);
        return mapper.aPublico(turnos.saveAndFlush(turno));
    }

    /** Idempotente sobre cancelado, incluso con menos de 24 h; la cancelación no envía mails. */
    public TurnoPublicoResponse cancelarPublico(String token) {
        Turno turno = porToken(token);
        if (!TransicionesTurno.cancelar(turno, CanceladoPor.PACIENTE)) {
            return mapper.aPublico(turno);
        }
        PlazosTurno.exigirCancelable(turno.getInicio(), clock);
        turno.setEstado(EstadoTurno.CANCELADO);
        turno.setCanceladoPor(CanceladoPor.PACIENTE);
        return mapper.aPublico(turnos.saveAndFlush(turno));
    }

    private Turno porToken(String token) {
        return turnos.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "TOKEN_INVALIDO", MSG_TOKEN_INVALIDO));
    }

    // ---- Acciones del odontólogo (C-08) ----

    /** Sin plazo de 48 h: el odontólogo confirma aunque falte más; sobre confirmado es idempotente. */
    public TurnoResponse confirmar(Long id) {
        Turno turno = porId(id);
        if (!TransicionesTurno.confirmar(turno, clock)) {
            return mapper.aAgenda(turno);
        }
        turno.setEstado(EstadoTurno.CONFIRMADO);
        return mapper.aAgenda(turnos.saveAndFlush(turno));
    }

    /** No idempotente: sobre cancelado o terminal responde 422; avisa siempre al paciente. */
    public TurnoResponse cancelar(Long id) {
        Turno turno = porId(id);
        TransicionesTurno.cancelar(turno, CanceladoPor.ODONTOLOGO);
        turno.setEstado(EstadoTurno.CANCELADO);
        turno.setCanceladoPor(CanceladoPor.ODONTOLOGO);
        Turno guardado = turnos.saveAndFlush(turno);
        notificaciones.cancelacionPorOdontologo(guardado);
        return mapper.aAgenda(guardado);
    }

    /** Solo desde la hora de inicio y sobre turnos activos. */
    public TurnoResponse marcarAsistencia(Long id, EstadoTurno estado) {
        Turno turno = porId(id);
        TransicionesTurno.marcarAsistencia(turno, estado, clock);
        turno.setEstado(estado);
        return mapper.aAgenda(turnos.saveAndFlush(turno));
    }

    /**
     * Mueve un turno activo: valida el nuevo horario excluyendo el propio turno y deja que la
     * restricción de exclusión de PostgreSQL traduzca el solapamiento concurrente a 409.
     */
    public TurnoResponse reprogramar(Long id, Instant inicio) {
        Turno turno = porId(id);
        TransicionesTurno.reprogramar(turno);
        disponibilidad.validarHorario(turno.getPrestacion(), inicio,
                ModoDisponibilidad.ODONTOLOGO, turno.getId());

        turno.setInicio(inicio);
        turno.setFin(inicio.plus(Duration.ofMinutes(turno.getPrestacion().getDuracionMinutos())));
        EstadoInicialTurno inicial = EstadoInicialTurno.calcular(inicio, clock);
        turno.setEstado(inicial.estado());
        turno.setRecordatorioEnviado(inicial.recordatorioEnviado());
        turno.setAlertaPendienteEnviada(inicial.alertaPendienteEnviada());
        Turno guardado = turnos.saveAndFlush(turno);

        if (inicial.estado() == EstadoTurno.PENDIENTE) {
            notificaciones.reprogramacion(guardado);
        } else {
            notificaciones.reprogramacionMenos24h(guardado);
        }
        return mapper.aAgenda(guardado);
    }

    // ---- Agenda del odontólogo (C-08) ----

    /** Lista los turnos con inicio en [desde, hasta) y, si se pide, solo los de un estado. */
    public List<TurnoResponse> listarAgenda(String desde, String hasta, String estado) {
        List<DetalleError> detalles = new ArrayList<>();
        LocalDate desdeFecha = fecha(desde, "desde", detalles);
        LocalDate hastaFecha = fecha(hasta, "hasta", detalles);
        EstadoTurno filtro = estado(estado, detalles);
        validarRango(desdeFecha, hastaFecha, detalles);
        if (!detalles.isEmpty()) {
            throw validacion(detalles);
        }

        Instant ini = desdeFecha.atStartOfDay(zona).toInstant();
        Instant fin = hastaFecha.plusDays(1).atStartOfDay(zona).toInstant();
        return turnos.findByInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(ini, fin).stream()
                .filter(t -> filtro == null || t.getEstado() == filtro)
                .map(mapper::aAgenda)
                .toList();
    }

    public TurnoResponse obtener(Long id) {
        return mapper.aAgenda(porId(id));
    }

    private Turno porId(Long id) {
        return turnos.findById(id).orElseThrow(this::noEncontrado);
    }

    private LocalDate fecha(String valor, String campo, List<DetalleError> detalles) {
        if (valor == null || valor.isBlank()) {
            detalles.add(new DetalleError(campo, "La fecha es obligatoria."));
            return null;
        }
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            detalles.add(new DetalleError(campo, "La fecha debe tener formato YYYY-MM-DD."));
            return null;
        }
    }

    private EstadoTurno estado(String valor, List<DetalleError> detalles) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return EstadoTurno.valueOf(valor);
        } catch (IllegalArgumentException e) {
            detalles.add(new DetalleError("estado", "El estado no es válido."));
            return null;
        }
    }

    private void validarRango(LocalDate desde, LocalDate hasta, List<DetalleError> detalles) {
        if (desde == null || hasta == null) {
            return;
        }
        if (hasta.isBefore(desde)) {
            detalles.add(new DetalleError("hasta", "hasta debe ser mayor o igual a desde."));
        } else if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAX_DIAS_AGENDA) {
            detalles.add(new DetalleError("hasta",
                    "El rango no puede superar los " + MAX_DIAS_AGENDA + " días."));
        }
    }

    private static ApiException validacion(List<DetalleError> detalles) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", MSG_VALIDACION, detalles);
    }

    private ApiException noEncontrado() {
        return new ApiException(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", MSG_NO_ENCONTRADO);
    }

    private Prestacion prestacion(Long id) {
        return prestaciones.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "RECURSO_NO_ENCONTRADO", MSG_NO_ENCONTRADO));
    }

    private Paciente resolverPacientePublico(ReservaPublicaRequest request) {
        String dni = NormalizacionDni.normalizar(request.dni());
        return pacientes.findByDni(dni)
                .orElseGet(() -> pacientes.save(new Paciente(request.nombre(), request.apellido(), dni)));
    }

    private Paciente resolverPacienteManual(TurnoManualRequest request) {
        if (request.pacienteId() != null) {
            return pacientes.findById(request.pacienteId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "RECURSO_NO_ENCONTRADO", MSG_NO_ENCONTRADO));
        }
        PacienteRequest nuevo = request.paciente();
        String dni = NormalizacionDni.normalizar(nuevo.dni());
        if (pacientes.findByDni(dni).isPresent()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "DNI_DUPLICADO", MSG_DNI_DUPLICADO);
        }
        return pacientes.save(new Paciente(nuevo.nombre(), nuevo.apellido(), dni));
    }

    private Turno construir(Paciente paciente, Prestacion prestacion, Instant inicio,
            String email, String telefono, Instant consentimientoAt) {
        EstadoInicialTurno inicial = EstadoInicialTurno.calcular(inicio, clock);
        Turno turno = new Turno(paciente, prestacion, inicio,
                inicio.plus(Duration.ofMinutes(prestacion.getDuracionMinutos())),
                inicial.estado(), generadorToken.generar(), email, telefono);
        turno.setConsentimientoAt(consentimientoAt);
        turno.setRecordatorioEnviado(inicial.recordatorioEnviado());
        turno.setAlertaPendienteEnviada(inicial.alertaPendienteEnviada());
        return turno;
    }

    /** Los avisos no revierten la reserva: el contrato de NotificacionTurnos nunca lanza por fallo SMTP. */
    private void avisar(Turno turno) {
        if (EstadoInicialTurno.supera48Horas(turno.getInicio(), clock)) {
            notificaciones.confirmacionReserva(turno);
            return;
        }
        if (EstadoInicialTurno.entre24y48Horas(turno.getInicio(), clock)) {
            turno.setRecordatorioEnviado(notificaciones.recordatorio(turno));
            return;
        }
        boolean enviado = notificaciones.avisoTurnoConfirmado(turno);
        turno.setRecordatorioEnviado(enviado);
        turno.setAlertaPendienteEnviada(enviado);
    }
}
