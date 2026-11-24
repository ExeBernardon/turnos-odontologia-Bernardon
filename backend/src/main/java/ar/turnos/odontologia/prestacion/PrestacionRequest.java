package ar.turnos.odontologia.prestacion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST/PUT /api/prestaciones (PrestacionRequest del contrato). {@code activa} por defecto true. */
public record PrestacionRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres.")
        String nombre,

        @NotNull(message = "La duración es obligatoria.")
        @Min(value = 1, message = "La duración debe ser de al menos 1 minuto.")
        @Max(value = 480, message = "La duración no puede superar los 480 minutos.")
        Integer duracionMinutos,

        Boolean activa) {
}
