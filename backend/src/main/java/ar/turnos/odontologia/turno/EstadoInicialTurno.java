package ar.turnos.odontologia.turno;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import ar.turnos.odontologia.config.AppProperties;

/**
 * Regla RN-TUR-11: estado inicial del turno segun la anticipacion, calculado con el
 * instante actual inyectado ({@link Clock}) para que sea determinista en tests.
 *
 * <ul>
 *   <li>Confirmacion o mas de anticipacion (24 h por defecto) -> {@code PENDIENTE}, flags de avisos en {@code false}.</li>
 *   <li>Menos de ese umbral -> {@code CONFIRMADO}, flags en {@code true} (el aviso ya se emitio al crear).</li>
 * </ul>
 *
 * Tambien expone las franjas de aviso del alta: mas del umbral de recordatorio (48 h por defecto),
 * entre el umbral de confirmacion y el de recordatorio (ambos inclusive) y menos del de confirmacion.
 * Los umbrales salen de la configuracion ({@link Umbrales#desde(AppProperties)}), los mismos que usa el job.
 */
public record EstadoInicialTurno(
        EstadoTurno estado,
        boolean recordatorioEnviado,
        boolean alertaPendienteEnviada) {

    /** Umbrales de anticipacion: bajo {@code confirmacion} nace confirmado; sobre {@code recordatorio} no hay recordatorio. */
    public record Umbrales(Duration confirmacion, Duration recordatorio) {

        public static final Umbrales POR_DEFECTO = new Umbrales(Duration.ofHours(24), Duration.ofHours(48));

        public static Umbrales desde(AppProperties app) {
            return new Umbrales(Duration.ofHours(app.alerta().pendienteHoras()),
                    Duration.ofHours(app.recordatorio().horasAntes()));
        }
    }

    public static EstadoInicialTurno calcular(Instant inicio, Clock reloj) {
        return calcular(inicio, reloj, Umbrales.POR_DEFECTO);
    }

    public static EstadoInicialTurno calcular(Instant inicio, Clock reloj, Umbrales umbrales) {
        boolean pendiente = anticipacion(inicio, reloj).compareTo(umbrales.confirmacion()) >= 0;
        return pendiente
                ? new EstadoInicialTurno(EstadoTurno.PENDIENTE, false, false)
                : new EstadoInicialTurno(EstadoTurno.CONFIRMADO, true, true);
    }

    public static EstadoTurno estado(Instant inicio, Clock reloj) {
        return calcular(inicio, reloj).estado();
    }

    /** True solo por encima de 48 h: el borde exacto pertenece a la franja de recordatorio. */
    public static boolean supera48Horas(Instant inicio, Clock reloj) {
        return superaRecordatorio(inicio, reloj, Umbrales.POR_DEFECTO);
    }

    /** True solo por encima del umbral de recordatorio: el borde exacto pertenece a la franja de recordatorio. */
    public static boolean superaRecordatorio(Instant inicio, Clock reloj, Umbrales umbrales) {
        return anticipacion(inicio, reloj).compareTo(umbrales.recordatorio()) > 0;
    }

    /** True entre 24 y 48 h, ambos inclusive. */
    public static boolean entre24y48Horas(Instant inicio, Clock reloj) {
        return enFranjaDeRecordatorio(inicio, reloj, Umbrales.POR_DEFECTO);
    }

    /** True entre el umbral de confirmacion y el de recordatorio, ambos inclusive. */
    public static boolean enFranjaDeRecordatorio(Instant inicio, Clock reloj, Umbrales umbrales) {
        Duration anticipacion = anticipacion(inicio, reloj);
        return anticipacion.compareTo(umbrales.confirmacion()) >= 0
                && anticipacion.compareTo(umbrales.recordatorio()) <= 0;
    }

    private static Duration anticipacion(Instant inicio, Clock reloj) {
        return Duration.between(reloj.instant(), inicio);
    }
}
