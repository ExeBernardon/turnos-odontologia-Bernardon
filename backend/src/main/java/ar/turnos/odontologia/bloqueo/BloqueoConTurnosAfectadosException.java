package ar.turnos.odontologia.bloqueo;

import java.util.List;

import org.springframework.http.HttpStatus;

import ar.turnos.odontologia.common.ApiException;

/** 409: el rango tiene turnos activos y no se confirmo su cancelacion. Lleva la lista para el cuerpo. */
public class BloqueoConTurnosAfectadosException extends ApiException {

    static final String CODIGO = "BLOQUEO_CON_TURNOS_AFECTADOS";
    static final String MENSAJE = "El rango tiene turnos activos. Confirmá la cancelación para crear el bloqueo.";

    private final transient List<TurnoAfectadoResponse> turnosAfectados;

    public BloqueoConTurnosAfectadosException(List<TurnoAfectadoResponse> turnosAfectados) {
        super(HttpStatus.CONFLICT, CODIGO, MENSAJE);
        this.turnosAfectados = List.copyOf(turnosAfectados);
    }

    public List<TurnoAfectadoResponse> getTurnosAfectados() {
        return turnosAfectados;
    }
}
