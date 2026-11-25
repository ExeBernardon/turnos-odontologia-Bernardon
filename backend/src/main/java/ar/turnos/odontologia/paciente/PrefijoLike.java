package ar.turnos.odontologia.paciente;

/** Escapa un texto para usarlo como prefijo literal en un {@code LIKE ... ESCAPE '\'}. */
final class PrefijoLike {

    private PrefijoLike() {
    }

    static String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
