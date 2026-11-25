package ar.turnos.odontologia.turno;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.turnos.odontologia.common.ApiException;
import ar.turnos.odontologia.disponibilidad.ContextoValidacion;
import ar.turnos.odontologia.disponibilidad.DisponibilidadService;
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

    private final TurnoRepository turnos;
    private final PacienteRepository pacientes;
    private final PrestacionRepository prestaciones;
    private final DisponibilidadService disponibilidad;
    private final NotificacionTurnos notificaciones;
    private final GeneradorToken generadorToken;
    private final TurnoMapper mapper;
    private final Clock clock;

    public TurnoService(TurnoRepository turnos, PacienteRepository pacientes,
            PrestacionRepository prestaciones, DisponibilidadService disponibilidad,
            NotificacionTurnos notificaciones, GeneradorToken generadorToken,
            TurnoMapper mapper, Clock clock) {
        this.turnos = turnos;
        this.pacientes = pacientes;
        this.prestaciones = prestaciones;
        this.disponibilidad = disponibilidad;
        this.notificaciones = notificaciones;
        this.generadorToken = generadorToken;
        this.mapper = mapper;
        this.clock = clock;
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
        Turno turno = turnos.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "TOKEN_INVALIDO", MSG_TOKEN_INVALIDO));
        return mapper.aPublico(turno);
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
