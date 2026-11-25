package ar.turnos.odontologia.bloqueo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.turno.EstadoTurno;
import ar.turnos.odontologia.turno.Turno;

/** Mapeo manual entidad a DTO con instantes en la zona del consultorio (offset -03:00). */
@Component
class BloqueoMapper {

    private static final Duration UMBRAL_PENDIENTE = Duration.ofHours(24);

    private final Clock clock;
    private final ZoneId zona;

    @Autowired
    BloqueoMapper(Clock clock, AppProperties app) {
        this(clock, app.zonaHoraria());
    }

    BloqueoMapper(Clock clock, ZoneId zona) {
        this.clock = clock;
        this.zona = zona;
    }

    BloqueoResponse aBloqueo(Bloqueo b) {
        return new BloqueoResponse(b.getId(), local(b.getInicio()), local(b.getFin()), b.getMotivo(),
                local(b.getCreadoEn()));
    }

    List<TurnoAfectadoResponse> aTurnos(List<Turno> turnos) {
        return turnos.stream().map(this::aTurno).toList();
    }

    TurnoAfectadoResponse aTurno(Turno t) {
        Paciente p = t.getPaciente();
        Prestacion pr = t.getPrestacion();
        return new TurnoAfectadoResponse(t.getId(), local(t.getInicio()), local(t.getFin()), t.getEstado(),
                t.getCanceladoPor(),
                new TurnoAfectadoResponse.PacienteRef(p.getId(), p.getNombre(), p.getApellido(), p.getDni()),
                new TurnoAfectadoResponse.PrestacionRef(pr.getId(), pr.getNombre(), pr.getDuracionMinutos()),
                t.getEmailContacto(), t.getTelefonoContacto(), local(t.getConsentimientoAt()),
                pendienteMenos24h(t), local(t.getCreadoEn()));
    }

    private boolean pendienteMenos24h(Turno t) {
        Instant ahora = clock.instant();
        return t.getEstado() == EstadoTurno.PENDIENTE && !t.getInicio().isBefore(ahora)
                && t.getInicio().isBefore(ahora.plus(UMBRAL_PENDIENTE));
    }

    private OffsetDateTime local(Instant i) {
        return i == null ? null : i.atZone(zona).toOffsetDateTime();
    }
}
