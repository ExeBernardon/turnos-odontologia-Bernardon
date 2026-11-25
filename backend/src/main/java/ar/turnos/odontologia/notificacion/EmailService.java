package ar.turnos.odontologia.notificacion;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import ar.turnos.odontologia.config.AppProperties;
import jakarta.mail.internet.MimeMessage;

/**
 * Transporte de los mails: renderiza la plantilla del {@link TipoMail}, arma el mensaje HTML UTF-8 con el
 * remitente {@code app.mail.from} y lo envia por SMTP. Nunca lanza: ante cualquier fallo (SMTP caido,
 * rechazo, plantilla rota) registra id del turno, tipo y clase de la excepcion y devuelve {@code false}.
 * No se loguea el mensaje de la excepcion (JavaMail incluye la direccion), ni destinatario, nombre, telefono
 * o token.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender sender;
    private final TemplateEngine motor;
    private final String remitente;

    public EmailService(JavaMailSender sender, TemplateEngine motor, AppProperties props) {
        this.sender = sender;
        this.motor = motor;
        this.remitente = props.mail().from();
    }

    /** @return {@code true} si el servidor SMTP acepto el mensaje, {@code false} si no salio. */
    public boolean enviar(TipoMail tipo, Long turnoId, String destinatario, Map<String, Object> modelo) {
        try {
            Context contexto = new Context();
            contexto.setVariables(modelo);
            String html = motor.process(tipo.plantilla(), contexto);

            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());
            helper.setFrom(remitente);
            helper.setTo(destinatario);
            helper.setSubject(tipo.asuntoPara(modelo));
            helper.setText(html, true);
            sender.send(mensaje);

            log.debug("Mail enviado: turnoId={} tipo={}", turnoId, tipo);
            return true;
        } catch (RuntimeException | jakarta.mail.MessagingException e) {
            log.warn("No se pudo enviar el mail: turnoId={} tipo={} causa={}", turnoId, tipo,
                    e.getClass().getSimpleName());
            return false;
        }
    }
}
