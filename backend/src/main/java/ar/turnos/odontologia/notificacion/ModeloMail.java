package ar.turnos.odontologia.notificacion;

import java.util.HashMap;
import java.util.Map;

import ar.turnos.odontologia.turno.Turno;

/**
 * Variables que reciben las plantillas de los mails. Solo incluye los links que el {@link TipoMail}
 * permite: asi "exactamente los botones de la tabla" se decide en un unico lugar y el token no llega
 * a las plantillas de los mails sin botones.
 */
final class ModeloMail {

    private ModeloMail() {
    }

    static Map<String, Object> de(TipoMail tipo, Turno turno, LinksTurno links, FormatoTurno formato,
            String contacto, int horasAlerta) {
        Map<String, Object> modelo = new HashMap<>();
        modelo.put("paciente", turno.getPaciente().getNombre() + " " + turno.getPaciente().getApellido());
        modelo.put("prestacion", turno.getPrestacion().getNombre());
        modelo.put("fechaHora", formato.fechaHora(turno.getInicio()));
        modelo.put("telefono", turno.getTelefonoContacto());
        modelo.put("contacto", contacto);
        if (tipo.llevaConfirmar()) {
            modelo.put("linkConfirmar", links.confirmar(turno.getToken()));
        }
        if (tipo.llevaCancelar()) {
            modelo.put("linkCancelar", links.cancelar(turno.getToken()));
        }
        if (tipo == TipoMail.TURNO_NO_CONFIRMADO) {
            modelo.put("horasAlerta", horasAlerta);
        }
        if (tipo == TipoMail.CANCELACION_BLOQUEO) {
            modelo.put("linkReservar", links.reservar());
        }
        return modelo;
    }
}
