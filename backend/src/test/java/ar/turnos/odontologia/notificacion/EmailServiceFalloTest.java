package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.exceptions.TemplateInputException;

import ar.turnos.odontologia.AppPropertiesFixture;
import ar.turnos.odontologia.turno.Turno;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(OutputCaptureExtension.class)
class EmailServiceFalloTest {

    private static final String DESTINO = "paciente@ejemplo.com";

    private final LinksTurno links = new LinksTurno("http://localhost:8080");
    private final FormatoTurno formato = new FormatoTurno(ZoneId.of("America/Argentina/Buenos_Aires"));
    private final Turno turno = MotorPlantillasPrueba.turno("Ana", MotorPlantillasPrueba.TOKEN);

    private JavaMailSender senderQueFalla(RuntimeException error) {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(error).when(sender).send(any(MimeMessage.class));
        return sender;
    }

    private Map<String, Object> modelo(TipoMail tipo) {
        return ModeloMail.de(tipo, turno, links, formato, AppPropertiesFixture.CONTACTO, 24);
    }

    @Test
    void unRechazoDelServidorDevuelveFalseSinLanzar() {
        EmailService servicio = new EmailService(senderQueFalla(new MailSendException("rechazado")),
                MotorPlantillasPrueba.motor(), AppPropertiesFixture.crear());

        assertThat(servicio.enviar(TipoMail.RECORDATORIO, 42L, DESTINO, modelo(TipoMail.RECORDATORIO))).isFalse();
    }

    @Test
    void cualquierErrorInesperadoDelEnvioTambienDevuelveFalse() {
        EmailService servicio = new EmailService(senderQueFalla(new IllegalStateException("inesperado")),
                MotorPlantillasPrueba.motor(), AppPropertiesFixture.crear());

        assertThat(servicio.enviar(TipoMail.AVISO_TURNO_CONFIRMADO, 43L, DESTINO,
                modelo(TipoMail.AVISO_TURNO_CONFIRMADO))).isFalse();
    }

    @Test
    void unaPlantillaQueNoSePuedeRenderizarDevuelveFalseYNoEnvia() {
        JavaMailSender sender = senderQueFalla(new MailSendException("no deberia llamarse"));
        TemplateEngine motorRoto = mock(TemplateEngine.class, invocation -> {
            throw new TemplateInputException("plantilla inexistente");
        });
        EmailService servicio = new EmailService(sender, motorRoto, AppPropertiesFixture.crear());

        assertThat(servicio.enviar(TipoMail.RECORDATORIO, 44L, DESTINO, modelo(TipoMail.RECORDATORIO))).isFalse();
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void elLogTieneIdYTipoPeroNingunDatoPersonalNiElToken(CapturedOutput salida) {
        String mensajeConDatos = "fallo al enviar a " + DESTINO + " tel 1155555555 DNI 99999999 token "
                + MotorPlantillasPrueba.TOKEN;
        EmailService servicio = new EmailService(senderQueFalla(new MailSendException(mensajeConDatos)),
                MotorPlantillasPrueba.motor(), AppPropertiesFixture.crear());

        servicio.enviar(TipoMail.RECORDATORIO, 42L, DESTINO, modelo(TipoMail.RECORDATORIO));

        assertThat(salida.getAll()).contains("42", "RECORDATORIO", "MailSendException")
                .doesNotContain(DESTINO, "1155555555", "99999999", "Ana", MotorPlantillasPrueba.TOKEN);
    }

    @Test
    void elLogDeOtroTipoYOtroTurnoTampocoFiltraDatos(CapturedOutput salida) {
        EmailService servicio = new EmailService(
                senderQueFalla(new MailSendException("a " + DESTINO + " " + MotorPlantillasPrueba.TOKEN)),
                MotorPlantillasPrueba.motor(), AppPropertiesFixture.crear());

        servicio.enviar(TipoMail.CANCELACION_BLOQUEO, 77L, DESTINO, modelo(TipoMail.CANCELACION_BLOQUEO));

        assertThat(salida.getAll()).contains("77", "CANCELACION_BLOQUEO")
                .doesNotContain(DESTINO, MotorPlantillasPrueba.TOKEN);
    }
}
