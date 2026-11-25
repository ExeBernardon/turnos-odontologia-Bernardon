package ar.turnos.odontologia.notificacion;

/**
 * Conteo de un ciclo de avisos programados, por tarea. "Fallidos" incluye tanto el aviso que no salio
 * (el envio devolvio {@code false}) como el turno cuyo procesamiento lanzo una excepcion. Los turnos que al
 * releerlos dejaron de ser elegibles no cuentan.
 */
public record ResultadoCiclo(int recordatoriosEnviados, int recordatoriosFallidos, int alertasEnviadas,
        int alertasFallidas) {

    boolean huboActividad() {
        return recordatoriosEnviados + recordatoriosFallidos + alertasEnviadas + alertasFallidas > 0;
    }
}
