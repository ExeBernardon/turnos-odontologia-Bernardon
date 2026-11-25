package ar.turnos.odontologia.notificacion;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * Arma los links al front que llevan los mails: confirmar y cancelar con el token del turno y la
 * reserva publica. La base es {@code APP_FRONTEND_BASE_URL}; la barra final no genera doble barra.
 * El token es Base64 URL-safe sin padding, por eso no necesita encoding.
 */
public class LinksTurno {

    private final String base;

    public LinksTurno(String frontendBaseUrl) {
        this.base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }

    public String confirmar(String token) {
        return conToken("/turno/confirmar", token);
    }

    public String cancelar(String token) {
        return conToken("/turno/cancelar", token);
    }

    public String reservar() {
        return UriComponentsBuilder.fromUriString(base).path("/reservar").build().toUriString();
    }

    private String conToken(String ruta, String token) {
        return UriComponentsBuilder.fromUriString(base).path(ruta).queryParam("token", token).build().toUriString();
    }
}
