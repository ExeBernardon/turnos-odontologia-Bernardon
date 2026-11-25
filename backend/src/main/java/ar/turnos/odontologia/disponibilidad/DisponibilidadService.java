package ar.turnos.odontologia.disponibilidad;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.turnos.odontologia.bloqueo.BloqueoRepository;
import ar.turnos.odontologia.common.ApiException;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.horario.HorarioAtencion;
import ar.turnos.odontologia.horario.HorarioAtencionRepository;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;
import ar.turnos.odontologia.turno.TurnoRepository;

/**
 * Fachada de disponibilidad: carga los datos del periodo con una lectura por repositorio y delega el
 * cálculo en {@link CalculoDisponibilidad}. También publica la validación de horario que usan la
 * reserva pública, la carga manual y la reprogramación (C-07/C-08).
 */
@Service
@Transactional(readOnly = true)
public class DisponibilidadService {

    static final int MAX_DIAS = 31;
    static final String MSG_NO_ENCONTRADO = "No encontramos el recurso solicitado.";
    static final String MSG_PRESTACION_INACTIVA = "La prestación no está disponible para reservar.";
    static final String MSG_VALIDACION = "Hay campos con errores.";

    private final PrestacionRepository prestaciones;
    private final HorarioAtencionRepository horarios;
    private final BloqueoRepository bloqueos;
    private final TurnoRepository turnos;
    private final Clock clock;
    private final ZoneId zona;
    private final CalculoDisponibilidad calculo;

    public DisponibilidadService(PrestacionRepository prestaciones, HorarioAtencionRepository horarios,
            BloqueoRepository bloqueos, TurnoRepository turnos, Clock clock, AppProperties app) {
        this.prestaciones = prestaciones;
        this.horarios = horarios;
        this.bloqueos = bloqueos;
        this.turnos = turnos;
        this.clock = clock;
        this.zona = app.zonaHoraria();
        this.calculo = new CalculoDisponibilidad(app.zonaHoraria(), app.agenda().pasoMinutos(),
                app.turno().anticipacionMinHoras(), app.turno().ventanaMaxDias());
    }

    // ---- API de dominio para C-07 / C-08 ----

    /** Valida un horario con la prestación ya resuelta; lanza 422 con la causa concreta si no sirve. */
    public void validarHorario(Prestacion prestacion, Instant inicio, ContextoValidacion contexto) {
        validarPrestacion(prestacion);
        LocalDate dia = LocalDate.ofInstant(inicio, zona);
        List<HorarioAtencion> delDia = horarios.findByDiaSemanaOrderByHoraInicio(dia.getDayOfWeek());
        List<Intervalo> ocupados = ocupantes(iniDelDia(dia), finDelDia(dia), contexto.turnoExcluidoId());

        ResultadoValidacion resultado = calculo.esValido(inicio, prestacion.getDuracionMinutos(), delDia,
                ocupados, contexto.modo(), clock.instant());
        if (!resultado.valido()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "HORARIO_NO_DISPONIBLE", resultado.motivo());
        }
    }

    /** Sobrecarga para reserva/carga manual sin exclusión de turno. */
    public void validarHorario(Prestacion prestacion, Instant inicio, ModoDisponibilidad modo) {
        validarHorario(prestacion, inicio, new ContextoValidacion(modo, null));
    }

    /** Sobrecarga de exclusión para reprogramación: el turno propio no cuenta como ocupante. */
    public void validarHorario(Prestacion prestacion, Instant inicio, ModoDisponibilidad modo, Long turnoExcluidoId) {
        validarHorario(prestacion, inicio, new ContextoValidacion(modo, turnoExcluidoId));
    }

    /** Variante que resuelve la prestación por id (404 si no existe) antes de validar. */
    public void validarHorario(Long prestacionId, Instant inicio, ContextoValidacion contexto) {
        validarHorario(resolver(prestacionId), inicio, contexto);
    }

    // ---- Consulta por rango ----

    /** Devuelve un elemento por fecha del rango inclusivo, con `horarios: []` cuando no hay lugar. */
    public DisponibilidadResponse consultar(Long prestacionId, LocalDate desde, LocalDate hasta,
            ModoDisponibilidad modo) {
        validarRango(desde, hasta);
        if (modo == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", MSG_VALIDACION);
        }
        Prestacion prestacion = resolver(prestacionId);
        validarPrestacion(prestacion);

        Instant ini = iniDelDia(desde);
        Instant fin = finDelDia(hasta);
        Map<DayOfWeek, List<HorarioAtencion>> porDia = horarios.findAll().stream()
                .collect(Collectors.groupingBy(HorarioAtencion::getDiaSemana));
        List<Intervalo> ocupados = ocupantes(ini, fin, null);
        Instant ahora = clock.instant();

        List<DiaDisponibleResponse> dias = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            List<Instant> libres = calculo.horariosLibres(dia, prestacion.getDuracionMinutos(),
                    porDia.getOrDefault(dia.getDayOfWeek(), List.of()), ocupados, modo, ahora);
            List<OffsetDateTime> horariosLocales = libres.stream()
                    .map(instante -> OffsetDateTime.ofInstant(instante, zona))
                    .toList();
            dias.add(new DiaDisponibleResponse(dia, horariosLocales));
        }
        return new DisponibilidadResponse(
                new PrestacionRefResponse(prestacion.getId(), prestacion.getNombre(),
                        prestacion.getDuracionMinutos()),
                List.copyOf(dias));
    }

    private List<Intervalo> ocupantes(Instant ini, Instant fin, Long turnoExcluidoId) {
        List<Intervalo> ocupados = new ArrayList<>();
        bloqueos.findQueIntersecan(ini, fin)
                .forEach(b -> ocupados.add(new Intervalo(b.getInicio(), b.getFin())));
        turnos.findActivosQueIntersecan(ini, fin).stream()
                .filter(t -> turnoExcluidoId == null || !turnoExcluidoId.equals(t.getId()))
                .forEach(t -> ocupados.add(new Intervalo(t.getInicio(), t.getFin())));
        return ocupados;
    }

    private Prestacion resolver(Long prestacionId) {
        if (prestacionId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", MSG_NO_ENCONTRADO);
        }
        Optional<Prestacion> encontrada = prestaciones.findById(prestacionId);
        return encontrada.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO",
                MSG_NO_ENCONTRADO));
    }

    private static void validarPrestacion(Prestacion prestacion) {
        if (prestacion == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", MSG_NO_ENCONTRADO);
        }
        if (!prestacion.isActiva()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRESTACION_INACTIVA", MSG_PRESTACION_INACTIVA);
        }
    }

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)
                || ChronoUnit.DAYS.between(desde, hasta) + 1 > MAX_DIAS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", MSG_VALIDACION);
        }
    }

    private Instant iniDelDia(LocalDate dia) {
        return dia.atStartOfDay(zona).toInstant();
    }

    private Instant finDelDia(LocalDate dia) {
        return dia.plusDays(1).atStartOfDay(zona).toInstant();
    }
}
