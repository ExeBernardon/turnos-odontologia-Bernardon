package ar.turnos.odontologia.turno;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Regla RN-TUR-11: estado inicial del turno segun la anticipacion, calculado con el
 * instante actual inyectado ({@link Clock}) para que sea determinista en tests.
 *
 * <ul>
 *   <li>24 h o mas antes del inicio -> {@code PENDIENTE}, flags de avisos en {@code false}.</li>
 *   <li>Menos de 24 h -> {@code CONFIRMADO}, flags en {@code true} (el aviso ya se emitio al crear).</li>
 * </ul>
 *
 * Tambien expone las franjas de aviso del alta: mas de 48 h, entre 24 y 48 h
 * (ambos inclusive) y menos de 24 h.
 */
public record EstadoInicialTurno(
        EstadoTurno estado,
        boolean recordatorioEnviado,
        boolean alertaPendienteEnviada) {

    private static final Duration UMBRAL_CONFIRMACION = Duration.ofHours(24);
    private static final Duration UMBRAL_RECORATORIO = Duration.ofHours(48);

    public static EstadoInicialTurno calcular(Instant inicio, Clock reloj) {
        boolean pendiente = anticipacion(inicio, reloj).compareTo(UMBRAL_CONFIRMACION) >= 0;
        return pendiente
                ? new EstadoInicialTurno(EstadoTurno.PENDIENTE, false, false)
                : new EstadoInicialTurno(EstadoTurno.CONFIRMADO, true, true);
    }

    public static EstadoTurno estado(Instant inicio, Clock reloj) {
        return calcular(inicio, reloj).estado();
    }

    /** True solo por encima de 48 h: el borde exacto pertenece a la franja de recordatorio. */
    public static boolean supera48Horas(Instant inicio, Clock reloj) {
        return anticipacion(inicio, reloj).compareTo(UMBRAL_RECORATORIO) > 0;
    }

    /** True entre 24 y 48 h, ambos inclusive. */
    public static boolean entre24y48Horas(Instant inicio, Clock reloj) {
        Duration anticipacion = anticipacion(inicio, reloj);
        return anticipacion.compareTo(UMBRAL_CONFIRMACION) >= 0
                && anticipacion.compareTo(UMBRAL_RECORATORIO) <= 0;
    }

    private static Duration anticipacion(Instant inicio, Clock reloj) {
        return Duration.between(reloj.instant(), inicio);
    }
}
