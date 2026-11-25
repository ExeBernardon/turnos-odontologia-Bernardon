package ar.turnos.odontologia.turno;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.validation.constraints.NotNull;

/** Request de reprogramación (contrato C-01): solo {@code inicio} con offset explícito. */
public record ReprogramarRequest(

        @NotNull @JsonDeserialize(using = InstanteConOffsetDeserializer.class) OffsetDateTime inicio) {
}
