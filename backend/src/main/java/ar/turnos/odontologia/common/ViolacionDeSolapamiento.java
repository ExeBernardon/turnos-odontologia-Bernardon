package ar.turnos.odontologia.common;

import java.sql.SQLException;

/**
 * Detecta la violacion de la restriccion de exclusion {@code turno_sin_solapamiento}
 * (SQLSTATE 23P01) en la cadena de causas de una excepcion.
 */
public final class ViolacionDeSolapamiento {

    static final String SQLSTATE_EXCLUSION = "23P01";

    private ViolacionDeSolapamiento() {
    }

    public static boolean es(Throwable e) {
        // Limite defensivo: una cadena de causas con ciclos no debe colgar el handler.
        Throwable actual = e;
        for (int i = 0; actual != null && i < 20; i++, actual = actual.getCause()) {
            if (actual instanceof SQLException sql && SQLSTATE_EXCLUSION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
