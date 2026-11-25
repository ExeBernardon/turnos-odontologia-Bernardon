package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.data.domain.Sort;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;

import ar.turnos.odontologia.AppPropertiesFixture;
import ar.turnos.odontologia.auth.Odontologo;
import ar.turnos.odontologia.auth.OdontologoRepository;
import ar.turnos.odontologia.turno.Turno;
import jakarta.mail.internet.MimeMessage;

/** Los 8 avisos por SMTP real en memoria, sobre un turno ficticio sin base de datos. */
class NotificacionTurnosEmailGreenMailTest {

    @RegisterExtension
    static final GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort());

    private NotificacionTurnosEmail notificaciones;
    private final Turno turno = MotorPlantillasPrueba.turno("Ana", MotorPlantillasPrueba.TOKEN);

    @BeforeEach
    void preparar() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("localhost");
        sender.setPort(greenMail.getSmtp().getPort());
        sender.setDefaultEncoding("UTF-8");
        OdontologoRepository odontologos = mock(OdontologoRepository.class);
        when(odontologos.findAll(any(Sort.class)))
                .thenReturn(List.of(new Odontologo("Dr. Ejemplo", "odontologo@ejemplo.com", "hash")));
        var props = AppPropertiesFixture.crear();
        notificaciones = new NotificacionTurnosEmail(new EmailService(sender, MotorPlantillasPrueba.motor(), props),
                odontologos, props);
    }

    private List<Predicate<Turno>> losOchoAvisos() {
        return List.of(notificaciones::confirmacionReserva, notificaciones::avisoTurnoConfirmado,
                notificaciones::recordatorio, notificaciones::reprogramacion, notificaciones::reprogramacionMenos24h,
                notificaciones::cancelacionPorOdontologo, notificaciones::cancelacionPorBloqueo,
                notificaciones::turnoNoConfirmado);
    }

    @Test
    void losOchoAvisosLlegaranConAsuntosDistintosYLosDestinatariosCorrectos() throws Exception {
        losOchoAvisos().forEach(aviso -> assertThat(aviso.test(turno)).isTrue());

        MimeMessage[] recibidos = greenMail.getReceivedMessages();
        assertThat(recibidos).hasSize(8);
        assertThat(Arrays.stream(recibidos).map(NotificacionTurnosEmailGreenMailTest::asunto)
                .collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(Arrays.stream(TipoMail.values()).map(TipoMail::asunto).toArray(String[]::new));

        Map<Boolean, List<String>> destinos = Arrays.stream(recibidos)
                .collect(Collectors.partitioningBy(m -> asunto(m).equals(TipoMail.TURNO_NO_CONFIRMADO.asunto()),
                        Collectors.mapping(NotificacionTurnosEmailGreenMailTest::destinatario, Collectors.toList())));
        assertThat(destinos.get(true)).containsExactly("odontologo@ejemplo.com");
        assertThat(destinos.get(false)).hasSize(7).containsOnly(MotorPlantillasPrueba.EMAIL_PACIENTE);
    }

    @Test
    void elMailAlOdontologoNoLlevaElTokenYElDelRecordatorioSi() throws Exception {
        notificaciones.turnoNoConfirmado(turno);
        notificaciones.recordatorio(turno);

        for (MimeMessage m : greenMail.getReceivedMessages()) {
            String contenido = (String) m.getContent();
            boolean esRecordatorio = asunto(m).equals(TipoMail.RECORDATORIO.asunto());
            assertThat(contenido.contains(MotorPlantillasPrueba.TOKEN)).isEqualTo(esRecordatorio);
        }
    }

    private static String asunto(MimeMessage m) {
        try {
            return m.getSubject();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String destinatario(MimeMessage m) {
        try {
            return m.getAllRecipients()[0].toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
