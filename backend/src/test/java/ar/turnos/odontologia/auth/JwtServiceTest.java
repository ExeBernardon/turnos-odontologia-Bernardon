package ar.turnos.odontologia.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import ar.turnos.odontologia.auth.JwtService.TokenEmitido;
import ar.turnos.odontologia.config.AppProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;

class JwtServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String SECRETO = "secreto-de-prueba-ficticio-de-al-menos-32-bytes-0123456789";
    private static final Instant AHORA = Instant.parse("2026-12-01T23:00:00Z");

    @Test
    void tokenValidoDevuelveSubjectNormalizadoYExpiraEnConOffsetDeLaZona() {
        JwtService service = service(Clock.fixed(AHORA, ZoneOffset.UTC));

        TokenEmitido emitido = service.emitir("  Odontologo@Ejemplo.com  ");

        assertThat(emitido.token().split("\\.")).hasSize(3);
        assertThat(service.subject(emitido.token())).isEqualTo("odontologo@ejemplo.com");
        assertThat(emitido.expiraEn()).isEqualTo(AHORA.plusSeconds(3600).atZone(ZONA).toOffsetDateTime());
        assertThat(emitido.expiraEn().getOffset()).isEqualTo(ZoneOffset.ofHours(-3));
    }

    @Test
    void tokenVencidoLanzaExpiredJwtException() {
        String token = service(Clock.fixed(AHORA, ZoneOffset.UTC)).emitir("odontologo@ejemplo.com").token();
        JwtService despues = service(Clock.fixed(AHORA.plusSeconds(3601), ZoneOffset.UTC));

        assertThatThrownBy(() -> despues.subject(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenConFirmaAlteradaLanzaSignatureException() {
        String token = service(Clock.fixed(AHORA, ZoneOffset.UTC)).emitir("odontologo@ejemplo.com").token();
        int inicioFirma = token.lastIndexOf('.') + 1;
        char original = token.charAt(inicioFirma);
        String alterado = token.substring(0, inicioFirma) + (original == 'A' ? 'B' : 'A')
                + token.substring(inicioFirma + 1);

        assertThatThrownBy(() -> service(Clock.fixed(AHORA, ZoneOffset.UTC)).subject(alterado))
                .isInstanceOf(SignatureException.class);
    }

    private JwtService service(Clock clock) {
        AppProperties app = new AppProperties(ZONA, "http://localhost:8080", null, null, null, null, null, null,
                null, new AppProperties.Jwt(SECRETO, 60), null);
        return new JwtService(app, clock);
    }
}
