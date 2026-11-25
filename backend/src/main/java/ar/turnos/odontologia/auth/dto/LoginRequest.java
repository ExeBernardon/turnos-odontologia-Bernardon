package ar.turnos.odontologia.auth.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import ar.turnos.odontologia.common.EmailNormalizador;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(max = 200) String password) {

    @JsonCreator
    public LoginRequest {
        email = email == null ? null : EmailNormalizador.normalizar(email);
    }
}
