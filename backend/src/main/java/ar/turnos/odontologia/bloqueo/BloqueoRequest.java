package ar.turnos.odontologia.bloqueo;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST /api/bloqueos/previsualizar (BloqueoRequest del contrato). */
public record BloqueoRequest(
        @NotNull(message = "El inicio es obligatorio.")
        Instant inicio,

        @NotNull(message = "El fin es obligatorio.")
        Instant fin,

        @Size(max = 200, message = "El motivo no puede superar los 200 caracteres.")
        String motivo) {
}
