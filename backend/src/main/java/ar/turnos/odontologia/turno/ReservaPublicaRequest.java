package ar.turnos.odontologia.turno;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request de la reserva publica (contrato C-01). El consentimiento se valida en el
 * service para responder 422 {@code CONSENTIMIENTO_REQUERIDO}, no 400.
 */
public record ReservaPublicaRequest(

        @NotBlank @Size(max = 100) String nombre,

        @NotBlank @Size(max = 100) String apellido,

        @NotBlank
        @Pattern(regexp = "^(\\d{7,8}|\\d{1,2}\\.\\d{3}\\.\\d{3})$") String dni,

        @NotBlank @Email @Size(max = 150)
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") String email,

        @NotBlank @Pattern(regexp = "^[0-9+()\\-\\s]{6,30}$") String telefono,

        @NotNull Long prestacionId,

        @NotNull @JsonDeserialize(using = InstanteConOffsetDeserializer.class) OffsetDateTime inicio,

        Boolean consentimiento) {
}
