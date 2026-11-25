package ar.turnos.odontologia.bloqueo;

import java.time.OffsetDateTime;

/** Bloqueo del contrato. */
public record BloqueoResponse(Long id, OffsetDateTime inicio, OffsetDateTime fin, String motivo,
        OffsetDateTime creadoEn) {
}
