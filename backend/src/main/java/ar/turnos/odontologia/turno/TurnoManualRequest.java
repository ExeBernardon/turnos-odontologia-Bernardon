package ar.turnos.odontologia.turno;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import ar.turnos.odontologia.paciente.PacienteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request de la carga manual autenticada: exactamente uno de {@code pacienteId} o
 * {@code paciente}. No exige consentimiento ni el minimo de anticipacion publico.
 */
public record TurnoManualRequest(

        @NotNull Long prestacionId,

        @NotNull @JsonDeserialize(using = InstanteConOffsetDeserializer.class) OffsetDateTime inicio,

        @NotBlank @Email @Size(max = 150)
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") String emailContacto,

        @NotBlank @Pattern(regexp = "^[0-9+()\\-\\s]{6,30}$") String telefonoContacto,

        Long pacienteId,

        @Valid PacienteRequest paciente) {

    @AssertTrue(message = "Enviar pacienteId o paciente, pero no ambos")
    @JsonIgnore
    public boolean getPacienteUnico() {
        return (pacienteId == null) != (paciente == null);
    }
}
