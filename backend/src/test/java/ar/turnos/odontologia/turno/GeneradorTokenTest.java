package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Opacidad del token de consulta (RN-TOK-01/02): 32 bytes, Base64 URL-safe y unico. */
class GeneradorTokenTest {

    private final GeneradorToken generador = new GeneradorToken();

    @Test
    void genera32BytesEnBase64UrlSinPadding() {
        String token = generador.generar();

        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void losTokensSonDistintos() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(generador.generar());
        }

        assertThat(tokens).hasSize(1000);
    }
}
