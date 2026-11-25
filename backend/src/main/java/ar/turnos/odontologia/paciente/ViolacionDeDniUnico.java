package ar.turnos.odontologia.paciente;

/** Detecta, en la cadena de causas, la violacion de la unique {@code paciente_dni_uk}. */
final class ViolacionDeDniUnico {

    static final String CONSTRAINT = "paciente_dni_uk";

    private ViolacionDeDniUnico() {
    }

    static boolean es(Throwable e) {
        // Limite defensivo: una cadena de causas con ciclos no debe colgar el service.
        Throwable actual = e;
        for (int i = 0; actual != null && i < 20; i++, actual = actual.getCause()) {
            String mensaje = actual.getMessage();
            if (mensaje != null && mensaje.contains(CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }
}
