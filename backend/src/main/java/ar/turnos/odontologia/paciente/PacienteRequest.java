package ar.turnos.odontologia.paciente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos minimos para el alta rapida de un paciente desde la agenda (US-010). */
public record PacienteRequest(

        @NotBlank @Size(max = 100) String nombre,

        @NotBlank @Size(max = 100) String apellido,

        @NotBlank
        @Pattern(regexp = "^(\\d{7,8}|\\d{1,2}\\.\\d{3}\\.\\d{3})$") String dni) {
}
