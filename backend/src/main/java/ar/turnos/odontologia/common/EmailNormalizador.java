package ar.turnos.odontologia.common;

import java.util.Locale;

public final class EmailNormalizador {

    private EmailNormalizador() {
    }

    public static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
