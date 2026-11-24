package ar.turnos.odontologia.horario;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Rango de atencion del contrato: hora local del consultorio en formato HH:mm. */
public record HorarioAtencionResponse(
        Long id,
        int diaSemana,
        @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
        @JsonFormat(pattern = "HH:mm") LocalTime horaFin) {
}
