package ar.turnos.odontologia.turno;

/**
 * Normaliza el DNI a 7 u 8 digitos (KB 04 / DD-12): acepta el formato con puntos
 * de la entrada publica y guarda solo los digitos. Nunca modifica el DNI de un
 * paciente ya existente.
 */
public final class NormalizacionDni {

    private NormalizacionDni() {
    }

    public static String normalizar(String dni) {
        if (dni == null || !dni.matches("[0-9.\\s]{7,20}")) {
            throw new IllegalArgumentException("DNI con formato invalido");
        }
        String digitos = dni.replaceAll("[^0-9]", "");
        if (!digitos.matches("[0-9]{7,8}")) {
            throw new IllegalArgumentException("El DNI debe tener 7 u 8 digitos");
        }
        return digitos;
    }
}
