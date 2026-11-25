package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.ZoneId;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;

import ar.turnos.odontologia.AppPropertiesFixture;
import ar.turnos.odontologia.turno.Turno;
import jakarta.mail.internet.MimeMessage;

/** SMTP real en memoria: verifica destinatario, remitente, asunto y codificacion UTF-8. */
class EmailServiceGreenMailTest {

    @RegisterExtension
    static final GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort());

    private final LinksTurno links = new LinksTurno("http://localhost:8080");
    private final FormatoTurno formato = new FormatoTurno(ZoneId.of("America/Argentina/Buenos_Aires"));

    private static JavaMailSenderImpl remitenteHacia(int puerto) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("localhost");
        sender.setPort(puerto);
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.connectiontimeout", "1000");
        props.put("mail.smtp.timeout", "1000");
        props.put("mail.smtp.writetimeout", "1000");
        return sender;
    }

    private EmailService servicioHacia(int puerto) {
        return new EmailService(remitenteHacia(puerto), MotorPlantillasPrueba.motor(),
                AppPropertiesFixture.crear());
    }

    private Map<String, Object> modelo(TipoMail tipo, Turno turno) {
        return ModeloMail.de(tipo, turno, links, formato, AppPropertiesFixture.CONTACTO, 24);
    }

    private static int puertoCerrado() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void entregaElMailAlDestinatarioConRemitenteAsuntoYAcentos() throws Exception {
        Turno turno = MotorPlantillasPrueba.turno("José", MotorPlantillasPrueba.TOKEN);

        boolean salio = servicioHacia(greenMail.getSmtp().getPort()).enviar(TipoMail.CONFIRMACION_RESERVA, 7L,
                "paciente@ejemplo.com", modelo(TipoMail.CONFIRMACION_RESERVA, turno));

        assertThat(salio).isTrue();
        MimeMessage[] recibidos = greenMail.getReceivedMessages();
        assertThat(recibidos).hasSize(1);
        MimeMessage mensaje = recibidos[0];
        assertThat(mensaje.getAllRecipients()[0].toString()).isEqualTo("paciente@ejemplo.com");
        assertThat(mensaje.getFrom()[0].toString()).isEqualTo("turnos@ejemplo.local");
        assertThat(mensaje.getSubject()).isEqualTo(TipoMail.CONFIRMACION_RESERVA.asunto());
        assertThat(mensaje.getContentType()).containsIgnoringCase("text/html");
        assertThat((String) mensaje.getContent()).contains("/turno/cancelar?token=" + MotorPlantillasPrueba.TOKEN);
        assertThat((String) mensaje.getContent()).contains("José Pérez");
    }

    @Test
    void otroTipoYOtroDestinatarioLlegaConSuPropioAsunto() throws Exception {
        Turno turno = MotorPlantillasPrueba.turno("Ana", MotorPlantillasPrueba.TOKEN);

        boolean salio = servicioHacia(greenMail.getSmtp().getPort()).enviar(TipoMail.TURNO_NO_CONFIRMADO, 8L,
                "odontologo@ejemplo.com", modelo(TipoMail.TURNO_NO_CONFIRMADO, turno));

        assertThat(salio).isTrue();
        MimeMessage mensaje = greenMail.getReceivedMessages()[0];
        assertThat(mensaje.getAllRecipients()[0].toString()).isEqualTo("odontologo@ejemplo.com");
        assertThat(mensaje.getSubject()).isEqualTo("Turno sin confirmar a menos de 24 h");
    }

    @Test
    void conElSmtpCaidoDevuelveFalseAntesDelTimeoutSinLanzar() throws Exception {
        Turno turno = MotorPlantillasPrueba.turno("Ana", MotorPlantillasPrueba.TOKEN);
        EmailService servicio = servicioHacia(puertoCerrado());

        long inicio = System.nanoTime();
        boolean salio = servicio.enviar(TipoMail.RECORDATORIO, 9L, "paciente@ejemplo.com",
                modelo(TipoMail.RECORDATORIO, turno));
        long milis = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(salio).isFalse();
        assertThat(milis).isLessThan(3000);
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }
}
