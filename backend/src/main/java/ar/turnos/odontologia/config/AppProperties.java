package ar.turnos.odontologia.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Parametros de negocio e infraestructura de KB 08 (variables de entorno). Cada clave se mapea
 * explicitamente a su variable en {@code application.yml}.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull ZoneId zonaHoraria,
        @NotBlank String frontendBaseUrl,
        @Valid @NotNull Agenda agenda,
        @Valid @NotNull Recordatorio recordatorio,
        @Valid @NotNull Alerta alerta,
        @Valid @NotNull Cancelacion cancelacion,
        @Valid @NotNull Turno turno,
        @Valid @NotNull Consultorio consultorio,
        @Valid @NotNull Mail mail,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Seed seed) {

    public record Agenda(@Min(1) int pasoMinutos) {
    }

    public record Recordatorio(@Min(1) int intervaloMin, @Min(1) int horasAntes) {
    }

    public record Alerta(@Min(1) int pendienteHoras) {
    }

    public record Cancelacion(@Min(0) int horasMinimas) {
    }

    public record Turno(@Min(0) int anticipacionMinHoras, @Min(1) int ventanaMaxDias) {
    }

    public record Consultorio(String contacto) {
    }

    public record Mail(@NotBlank String from) {
    }

    public record Jwt(@NotBlank String secret, @Min(1) int expirationMinutes) {
    }

    public record Seed(@Valid @NotNull Odontologo odontologo) {

        public record Odontologo(@NotBlank String nombre, @NotBlank String email, @NotBlank String password) {
        }
    }
}
