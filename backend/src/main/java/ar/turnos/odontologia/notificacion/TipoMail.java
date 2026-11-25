package ar.turnos.odontologia.notificacion;

import java.util.Map;

/**
 * Los 8 mails de la tabla "Mails del sistema" (KB 05). Es la unica fuente de la plantilla, el asunto y
 * los botones de cada uno: solo el Recordatorio lleva Confirmar; Confirmacion de reserva, Reprogramacion
 * y Recordatorio llevan Cancelar; el resto no lleva botones.
 */
public enum TipoMail {

    CONFIRMACION_RESERVA("confirmacion-reserva", "Confirmamos tu reserva de turno", false, true),
    AVISO_TURNO_CONFIRMADO("aviso-turno-confirmado", "Tu turno quedó reservado", false, false),
    RECORDATORIO("recordatorio", "Recordatorio de tu turno: confirmalo o cancelalo", true, true),
    REPROGRAMACION("reprogramacion", "Reprogramamos tu turno", false, true),
    REPROGRAMACION_MENOS_24H("reprogramacion-menos-24h", "Reprogramamos tu turno para dentro de poco", false, false),
    CANCELACION_ODONTOLOGO("cancelacion-odontologo", "Cancelamos tu turno", false, false),
    CANCELACION_BLOQUEO("cancelacion-bloqueo", "Tuvimos que cancelar tu turno: reserva uno nuevo", false, false),
    TURNO_NO_CONFIRMADO("turno-no-confirmado", "Turno sin confirmar a menos de %d h", false, false);

    private final String plantilla;
    private final String asunto;
    private final boolean confirmar;
    private final boolean cancelar;

    TipoMail(String plantilla, String asunto, boolean confirmar, boolean cancelar) {
        this.plantilla = "email/" + plantilla;
        this.asunto = asunto;
        this.confirmar = confirmar;
        this.cancelar = cancelar;
    }

    public String plantilla() {
        return plantilla;
    }

    public String asunto() {
        return asunto;
    }

    /**
     * Asunto final del mail. El de {@link #TURNO_NO_CONFIRMADO} lleva el umbral de la alerta
     * ({@code horasAlerta} del modelo); el resto es fijo.
     */
    public String asuntoPara(Map<String, Object> modelo) {
        if (this == TURNO_NO_CONFIRMADO) {
            return String.format(asunto, modelo.get("horasAlerta"));
        }
        return asunto;
    }

    public boolean llevaConfirmar() {
        return confirmar;
    }

    public boolean llevaCancelar() {
        return cancelar;
    }
}
