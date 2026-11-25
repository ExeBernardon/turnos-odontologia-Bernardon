package ar.turnos.odontologia.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import ar.turnos.odontologia.common.EmailNormalizador;
import ar.turnos.odontologia.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    public record TokenEmitido(String token, OffsetDateTime expiraEn) {
    }

    private final AppProperties app;
    private final Clock clock;
    private final SecretKey clave;

    public JwtService(AppProperties app, Clock clock) {
        this.app = app;
        this.clock = clock;
        this.clave = Keys.hmacShaKeyFor(app.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public TokenEmitido emitir(String email) {
        Instant ahora = clock.instant();
        Instant expira = ahora.plusSeconds(app.jwt().expirationMinutes() * 60L);
        String token = Jwts.builder()
                .subject(EmailNormalizador.normalizar(email))
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expira))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
        return new TokenEmitido(token, expira.atZone(app.zonaHoraria()).toOffsetDateTime());
    }

    public String subject(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(clave)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token);
        return jws.getPayload().getSubject();
    }
}
