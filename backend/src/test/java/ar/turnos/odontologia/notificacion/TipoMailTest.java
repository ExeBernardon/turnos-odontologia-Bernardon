package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class TipoMailTest {

    @Test
    void hayExactamenteUnTipoPorCadaMailDeKb05() {
        assertThat(TipoMail.values()).hasSize(8);
    }

    @Test
    void cadaTipoTieneAsuntoYPlantillaPropios() {
        Set<String> asuntos = Arrays.stream(TipoMail.values()).map(TipoMail::asunto).collect(Collectors.toSet());
        Set<String> plantillas = Arrays.stream(TipoMail.values()).map(TipoMail::plantilla).collect(Collectors.toSet());

        assertThat(asuntos).hasSize(8).allSatisfy(a -> assertThat(a).isNotBlank());
        assertThat(plantillas).hasSize(8).allSatisfy(p -> assertThat(p).startsWith("email/"));
    }

    @Test
    void elAvisoDeTurnoConfirmadoLlevaTildeEnElAsunto() {
        assertThat(TipoMail.AVISO_TURNO_CONFIRMADO.asunto()).isEqualTo("Tu turno quedó reservado");
    }

    @Test
    void elAsuntoDelTurnoNoConfirmadoMuestraLasHorasDeLaAlerta() {
        assertThat(TipoMail.TURNO_NO_CONFIRMADO.asuntoPara(Map.of("horasAlerta", 24)))
                .isEqualTo("Turno sin confirmar a menos de 24 h");
        assertThat(TipoMail.TURNO_NO_CONFIRMADO.asuntoPara(Map.of("horasAlerta", 12)))
                .isEqualTo("Turno sin confirmar a menos de 12 h");
    }

    @Test
    void losAsuntosFijosNoCambianConElModelo() {
        assertThat(TipoMail.RECORDATORIO.asuntoPara(Map.of("horasAlerta", 12)))
                .isEqualTo("Recordatorio de tu turno: confirmalo o cancelalo");
        assertThat(TipoMail.CONFIRMACION_RESERVA.asuntoPara(Map.of()))
                .isEqualTo(TipoMail.CONFIRMACION_RESERVA.asunto());
    }

    @Test
    void soloElRecordatorioLlevaConfirmar() {
        Set<TipoMail> conConfirmar = EnumSet.noneOf(TipoMail.class);
        for (TipoMail t : TipoMail.values()) {
            if (t.llevaConfirmar()) {
                conConfirmar.add(t);
            }
        }
        assertThat(conConfirmar).containsExactly(TipoMail.RECORDATORIO);
    }

    @Test
    void cancelarSoloEnRecordatorioConfirmacionDeReservaYReprogramacion() {
        Set<TipoMail> conCancelar = EnumSet.noneOf(TipoMail.class);
        for (TipoMail t : TipoMail.values()) {
            if (t.llevaCancelar()) {
                conCancelar.add(t);
            }
        }
        assertThat(conCancelar).containsExactlyInAnyOrder(TipoMail.RECORDATORIO, TipoMail.CONFIRMACION_RESERVA,
                TipoMail.REPROGRAMACION);
    }
}
