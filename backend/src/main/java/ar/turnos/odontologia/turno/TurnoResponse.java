package ar.turnos.odontologia.turno;

import java.time.OffsetDateTime;

/** Turno de la agenda (vista del odontologo) segun el contrato. Nunca incluye el token. */
public record TurnoResponse(
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
