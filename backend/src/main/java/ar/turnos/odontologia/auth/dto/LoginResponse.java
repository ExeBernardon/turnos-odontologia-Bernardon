package ar.turnos.odontologia.auth.dto;

import java.time.OffsetDateTime;

public record LoginResponse(String token, String tipo, OffsetDateTime expiraEn, String nombre) {
}
