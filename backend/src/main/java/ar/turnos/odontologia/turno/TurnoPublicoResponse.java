package ar.turnos.odontologia.turno;

import java.time.OffsetDateTime;

/**
 * Resumen publico del contrato, tanto para el alta como para la consulta por token.
 * No incluye DNI, apellido, email, telefono, ids internos ni el token (RN-TOK-02).
 */
public record TurnoPublicoResponse(
        PrestacionResumen prestacion,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        EstadoTurno estado,
        String nombrePaciente,
        boolean puedeConfirmar,
        boolean puedeCancelar,
        String contactoConsultorio) {

    public record PrestacionResumen(String nombre, int duracionMinutos) {
    }
}
