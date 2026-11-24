package ar.turnos.odontologia.notificacion;

import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;

import ar.turnos.odontologia.turno.Turno;

/**
 * Implementacion por defecto hasta que exista la real (C-11): no envia nada ni registra datos del turno.
 * Devuelve {@code true} para que los flags de los callers se comporten como en produccion. {@link Fallback}
 * hace que cualquier otra implementacion registrada la reemplace sin {@code @Primary}.
 */
@Component
@Fallback
public class NotificacionTurnosNoOp implements NotificacionTurnos {

    @Override
    public boolean confirmacionReserva(Turno turno) {
        return true;
    }

    @Override
    public boolean avisoTurnoConfirmado(Turno turno) {
        return true;
    }

    @Override
    public boolean recordatorio(Turno turno) {
        return true;
    }

    @Override
    public boolean reprogramacion(Turno turno) {
        return true;
    }

    @Override
    public boolean reprogramacionMenos24h(Turno turno) {
        return true;
    }

    @Override
    public boolean cancelacionPorOdontologo(Turno turno) {
        return true;
    }

    @Override
    public boolean cancelacionPorBloqueo(Turno turno) {
        return true;
    }

    @Override
    public boolean turnoNoConfirmado(Turno turno) {
        return true;
    }
}
