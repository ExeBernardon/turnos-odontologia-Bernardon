package ar.turnos.odontologia.turno;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * Genera el token opaco de consulta del turno (RN-TOK-01/02): 32 bytes aleatorios
 * criptograficamente seguros, codificados en Base64 URL-safe sin padding.
 * El resultado solo viaja por el link del email; nunca se devuelve por la API ni se loguea.
 */
@Component
public class GeneradorToken {

    private static final int BYTES = 32;

    private final SecureRandom aleatorio = new SecureRandom();
    private final Base64.Encoder codificador = Base64.getUrlEncoder().withoutPadding();

    public String generar() {
        byte[] bytes = new byte[BYTES];
        aleatorio.nextBytes(bytes);
        return codificador.encodeToString(bytes);
    }
}
