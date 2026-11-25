package ar.turnos.odontologia.notificacion;

import ar.turnos.odontologia.turno.Turno;

/**
 * Punto unico por el que los dominios piden avisar sobre un turno: un metodo por cada fila de la tabla
 * "Mails del sistema" de KB 05. Los invocan C-07 (reserva), C-08 (reprogramar/cancelar), C-10 (bloqueos)
 * y el job de C-12.
 *
 * <p>Contrato comun a todos los metodos:
 * <ul>
 * <li>Se invocan dentro de la transaccion del caller, despues del {@code saveAndFlush} del turno, de modo
 * que la implementacion real pueda leer {@code paciente} y {@code prestacion} (LAZY).</li>
 * <li>Devuelven {@code true} si el aviso salio y {@code false} si fallo el envio. Nunca lanzan por un
 * fallo de envio: los callers usan el resultado para los flags {@code recordatorio_enviado} y
 * {@code alerta_pendiente_enviada} y el job reintenta (RN-NOT-02, RN-NOT-05).</li>
 * <li>El destinatario es siempre el {@code email_contacto} del turno, salvo {@link #turnoNoConfirmado}
 * (el odontologo).</li>
 * </ul>
 */
public interface NotificacionTurnos {

    /** Mail 1, al paciente: confirmacion de reserva con mas de 48 h de anticipacion (turno PENDIENTE). Solo Cancelar. */
    boolean confirmacionReserva(Turno turno);

    /** Mail 1b, al paciente: aviso de turno creado con menos de 24 h (turno CONFIRMADO). Sin botones. */
    boolean avisoTurnoConfirmado(Turno turno);

    /** Mail 2, al paciente: recordatorio de un turno PENDIENTE, con Confirmar y Cancelar. Lo reintenta el job. */
    boolean recordatorio(Turno turno);

    /** Mail 3, al paciente: el odontologo reprogramo a 24 h o mas (turno PENDIENTE). Solo Cancelar. */
    boolean reprogramacion(Turno turno);

    /** Mail 3b, al paciente: el odontologo reprogramo a menos de 24 h (turno CONFIRMADO). Sin botones. */
    boolean reprogramacionMenos24h(Turno turno);

    /** Mail 4, al paciente: el odontologo cancelo el turno desde la agenda. Sin botones. */
    boolean cancelacionPorOdontologo(Turno turno);

    /** Mail 5, al paciente: el turno se cancelo al confirmar un bloqueo. Invita a reservar de nuevo. */
    boolean cancelacionPorBloqueo(Turno turno);

    /** Mail 6, al odontologo: a 24 h del inicio el turno sigue PENDIENTE. Lleva paciente, prestacion, horario y telefono. */
    boolean turnoNoConfirmado(Turno turno);
}
