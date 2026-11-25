package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import ar.turnos.odontologia.turno.Turno;

/** Renderiza las plantillas reales con un TemplateEngine armado a mano (sin Spring ni SMTP). */
class PlantillasMailTest {

    private static final String BASE = "http://localhost:8080";
    private static final String TOKEN = "tokFicticio-123_abc";
    private static final String CONTACTO = "Consultorio de ejemplo - Tel. 11 0000-0000";
    private static final String TELEFONO = MotorPlantillasPrueba.TELEFONO;

    private final TemplateEngine motor = MotorPlantillasPrueba.motor();
    private final LinksTurno links = new LinksTurno(BASE);
    private final FormatoTurno formato = new FormatoTurno(ZoneId.of("America/Argentina/Buenos_Aires"));

    private String render(TipoMail tipo, Turno turno) {
        Context ctx = new Context();
        ctx.setVariables(ModeloMail.de(tipo, turno, links, formato, CONTACTO, 24));
        return motor.process(tipo.plantilla(), ctx);
    }

    @ParameterizedTest
    @EnumSource(TipoMail.class)
    void llevaExactamenteLosBotonesDeSuTipoYElTokenSoloSiHayBotones(TipoMail tipo) {
        String html = render(tipo, MotorPlantillasPrueba.turno("Ana", TOKEN));

        assertThat(html.contains("/turno/confirmar?token=" + TOKEN)).isEqualTo(tipo.llevaConfirmar());
        assertThat(html.contains("/turno/cancelar?token=" + TOKEN)).isEqualTo(tipo.llevaCancelar());
        if (!tipo.llevaConfirmar() && !tipo.llevaCancelar()) {
            assertThat(html).doesNotContain(TOKEN);
        }
    }

    @ParameterizedTest
    @EnumSource(TipoMail.class)
    void muestraPacientePrestacionYHorarioEnBuenosAires(TipoMail tipo) {
        String html = render(tipo, MotorPlantillasPrueba.turno("Ana", TOKEN));

        assertThat(html).contains("Ana Pérez", "Limpieza dental", "10 de diciembre de 2026", "10:00");
    }

    @Test
    void elTurnoNoConfirmadoMuestraElTelefonoDelTurno() {
        assertThat(render(TipoMail.TURNO_NO_CONFIRMADO, MotorPlantillasPrueba.turno("Ana", TOKEN))).contains(TELEFONO);
    }

    @Test
    void elTurnoNoConfirmadoMuestraLasHorasDeLaAlertaConfigurada() {
        Turno turno = MotorPlantillasPrueba.turno("Ana", TOKEN);

        assertThat(renderNoConfirmado(turno, 24)).contains("24 horas");
        assertThat(renderNoConfirmado(turno, 12)).contains("12 horas").doesNotContain("24 horas");
    }

    private String renderNoConfirmado(Turno turno, int horasAlerta) {
        Context ctx = new Context();
        ctx.setVariables(ModeloMail.de(TipoMail.TURNO_NO_CONFIRMADO, turno, links, formato, CONTACTO, horasAlerta));
        return motor.process(TipoMail.TURNO_NO_CONFIRMADO.plantilla(), ctx);
    }

    @Test
    void elTelefonoDelTurnoSeReflejaEnElMail6() {
        Turno otro = MotorPlantillasPrueba.turno("Luis", TOKEN);
        otro.setTelefonoContacto("1144444444");

        assertThat(render(TipoMail.TURNO_NO_CONFIRMADO, otro)).contains("1144444444").doesNotContain(TELEFONO);
    }

    @Test
    void laCancelacionPorBloqueoInvitaAReservarConElLinkPublico() {
        String html = render(TipoMail.CANCELACION_BLOQUEO, MotorPlantillasPrueba.turno("Ana", TOKEN));

        assertThat(html).contains(BASE + "/reservar").containsIgnoringCase("reserv");
    }

    @ParameterizedTest
    @EnumSource(value = TipoMail.class, names = { "AVISO_TURNO_CONFIRMADO", "REPROGRAMACION_MENOS_24H",
            "CANCELACION_ODONTOLOGO", "CANCELACION_BLOQUEO" })
    void losMailsSinBotonesAlPacienteMuestranElContactoDelConsultorio(TipoMail tipo) {
        assertThat(render(tipo, MotorPlantillasPrueba.turno("Ana", TOKEN))).contains(CONTACTO);
    }

    @Test
    void losMailsConBotonesNoReservanLinkDeReservaPublica() {
        Set<TipoMail> conBotones = EnumSet.of(TipoMail.RECORDATORIO, TipoMail.CONFIRMACION_RESERVA,
                TipoMail.REPROGRAMACION);
        for (TipoMail tipo : conBotones) {
            assertThat(render(tipo, MotorPlantillasPrueba.turno("Ana", TOKEN))).doesNotContain("/reservar");
        }
    }

    @ParameterizedTest
    @EnumSource(TipoMail.class)
    void escapaElHtmlDelNombreDelPaciente(TipoMail tipo) {
        String html = render(tipo, MotorPlantillasPrueba.turno("<b>Ficticio</b>", TOKEN));

        assertThat(html).contains("&lt;b&gt;Ficticio&lt;/b&gt;").doesNotContain("<b>Ficticio</b>");
    }
}
