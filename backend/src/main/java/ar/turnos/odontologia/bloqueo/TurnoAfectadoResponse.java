package ar.turnos.odontologia.bloqueo;

import java.time.OffsetDateTime;

import ar.turnos.odontologia.turno.CanceladoPor;
import ar.turnos.odontologia.turno.EstadoTurno;

/** Turno del contrato (vista del odontologo) tal como lo devuelven los bloqueos. Nunca incluye el token. */
public record TurnoAfectadoResponse(
        Long id,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        EstadoTurno estado,
        CanceladoPor canceladoPor,
        PacienteRef paciente,
        PrestacionRef prestacion,
        String emailContacto,
        String telefonoContacto,
        OffsetDateTime consentimientoAt,
        boolean pendienteMenos24h,
        OffsetDateTime creadoEn) {

    public record PacienteRef(Long id, String nombre, String apellido, String dni) {
    }

    public record PrestacionRef(Long id, String nombre, int duracionMinutos) {
    }
}
