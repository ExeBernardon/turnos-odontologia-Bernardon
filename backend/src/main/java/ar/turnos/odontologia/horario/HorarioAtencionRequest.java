package ar.turnos.odontologia.horario;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de POST/PUT /api/horarios-atencion. diaSemana ISO: 1 = lunes ... 7 = domingo. */
public record HorarioAtencionRequest(
        @NotNull(message = "El día de la semana es obligatorio.")
        @Min(value = 1, message = "El día de la semana debe estar entre 1 (lunes) y 7 (domingo).")
        @Max(value = 7, message = "El día de la semana debe estar entre 1 (lunes) y 7 (domingo).")
        Integer diaSemana,

        @NotNull(message = "La hora de inicio es obligatoria.")
        @JsonFormat(pattern = "HH:mm")
        LocalTime horaInicio,

        @NotNull(message = "La hora de fin es obligatoria.")
        @JsonFormat(pattern = "HH:mm")
        LocalTime horaFin) {
}
