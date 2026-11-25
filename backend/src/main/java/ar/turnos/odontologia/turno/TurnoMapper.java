package ar.turnos.odontologia.turno;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.prestacion.Prestacion;

/** Mapeo manual entidad a DTO, con instantes en la zona del consultorio. */
@Component
public class TurnoMapper {

    private static final Duration UMBRAL_24H = Duration.ofHours(24);
    private static final Duration UMBRAL_48H = Duration.ofHours(48);

    private final Clock clock;
    private final ZoneId zona;
    private final String contacto;

    @Autowired
    public TurnoMapper(Clock clock, AppProperties app) {
        this(clock, app.zonaHoraria(), app.consultorio().contacto());
    }

    public TurnoMapper(Clock clock, ZoneId zona, String contacto) {
        this.clock = clock;
        this.zona = zona;
        this.contacto = contacto;
    }

    public TurnoPublicoResponse aPublico(Turno t) {
        Duration anticipacion = Duration.between(clock.instant(), t.getInicio());
        boolean pendiente = t.getEstado() == EstadoTurno.PENDIENTE;
        Prestacion prestacion = t.getPrestacion();
        return new TurnoPublicoResponse(
                new TurnoPublicoResponse.PrestacionResumen(prestacion.getNombre(),
                        prestacion.getDuracionMinutos()),
                local(t.getInicio()), local(t.getFin()), t.getEstado(), t.getPaciente().getNombre(),
                pendiente && !anticipacion.isNegative() && anticipacion.compareTo(UMBRAL_48H) <= 0,
                pendiente && anticipacion.compareTo(UMBRAL_24H) >= 0,
                contacto);
    }

    public TurnoResponse aAgenda(Turno t) {
        Paciente paciente = t.getPaciente();
        Prestacion prestacion = t.getPrestacion();
        return new TurnoResponse(t.getId(), local(t.getInicio()), local(t.getFin()), t.getEstado(),
                t.getCanceladoPor(),
                new TurnoResponse.PacienteRef(paciente.getId(), paciente.getNombre(), paciente.getApellido(),
                        paciente.getDni()),
                new TurnoResponse.PrestacionRef(prestacion.getId(), prestacion.getNombre(),
                        prestacion.getDuracionMinutos()),
                t.getEmailContacto(), t.getTelefonoContacto(), local(t.getConsentimientoAt()),
                pendienteMenos24h(t), local(t.getCreadoEn()));
    }

    private boolean pendienteMenos24h(Turno t) {
        Instant ahora = clock.instant();
        return t.getEstado() == EstadoTurno.PENDIENTE && !t.getInicio().isBefore(ahora)
                && t.getInicio().isBefore(ahora.plus(UMBRAL_24H));
    }

    private OffsetDateTime local(Instant instante) {
        return instante == null ? null : instante.atZone(zona).toOffsetDateTime();
    }
}
