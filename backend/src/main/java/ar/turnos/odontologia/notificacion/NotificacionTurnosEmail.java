package ar.turnos.odontologia.notificacion;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import ar.turnos.odontologia.auth.Odontologo;
import ar.turnos.odontologia.auth.OdontologoRepository;
import ar.turnos.odontologia.config.AppProperties;
import ar.turnos.odontologia.turno.Turno;

/**
 * Implementacion real de {@link NotificacionTurnos}: un mail por cada fila de la tabla "Mails del sistema".
 * Sincrona, dentro de la transaccion del caller (lee {@code paciente} y {@code prestacion} LAZY) y sin
 * lanzar por fallos de envio: devuelve lo que informa {@link EmailService}. Los mails al paciente van
 * solo al {@code email_contacto} del turno; "Turno no confirmado" va al email del odontologo.
 */
@Component
public class NotificacionTurnosEmail implements NotificacionTurnos {

    private static final Logger log = LoggerFactory.getLogger(NotificacionTurnosEmail.class);

    private final EmailService emails;
    private final OdontologoRepository odontologos;
    private final LinksTurno links;
    private final FormatoTurno formato;
    private final String contacto;
    private final int horasAlerta;

    public NotificacionTurnosEmail(EmailService emails, OdontologoRepository odontologos, AppProperties props) {
        this.emails = emails;
        this.odontologos = odontologos;
        this.links = new LinksTurno(props.frontendBaseUrl());
        this.formato = new FormatoTurno(props.zonaHoraria());
        this.contacto = props.consultorio().contacto();
        this.horasAlerta = props.alerta().pendienteHoras();
    }

    @Override
    public boolean confirmacionReserva(Turno turno) {
        return alPaciente(TipoMail.CONFIRMACION_RESERVA, turno);
    }

    @Override
    public boolean avisoTurnoConfirmado(Turno turno) {
        return alPaciente(TipoMail.AVISO_TURNO_CONFIRMADO, turno);
    }

    @Override
    public boolean recordatorio(Turno turno) {
        return alPaciente(TipoMail.RECORDATORIO, turno);
    }

    @Override
    public boolean reprogramacion(Turno turno) {
        return alPaciente(TipoMail.REPROGRAMACION, turno);
    }

    @Override
    public boolean reprogramacionMenos24h(Turno turno) {
        return alPaciente(TipoMail.REPROGRAMACION_MENOS_24H, turno);
    }

    @Override
    public boolean cancelacionPorOdontologo(Turno turno) {
        return alPaciente(TipoMail.CANCELACION_ODONTOLOGO, turno);
    }

    @Override
    public boolean cancelacionPorBloqueo(Turno turno) {
        return alPaciente(TipoMail.CANCELACION_BLOQUEO, turno);
    }

    @Override
    public boolean turnoNoConfirmado(Turno turno) {
        Optional<String> email = odontologos.findAll(Sort.by("id")).stream().findFirst().map(Odontologo::getEmail);
        if (email.isEmpty()) {
            log.warn("No se envio el mail: no hay odontologo registrado. turnoId={} tipo={}", turno.getId(),
                    TipoMail.TURNO_NO_CONFIRMADO);
            return false;
        }
        return enviar(TipoMail.TURNO_NO_CONFIRMADO, turno, email.get());
    }

    private boolean alPaciente(TipoMail tipo, Turno turno) {
        return enviar(tipo, turno, turno.getEmailContacto());
    }

    private boolean enviar(TipoMail tipo, Turno turno, String destinatario) {
        return emails.enviar(tipo, turno.getId(), destinatario, ModeloMail.de(tipo, turno, links, formato, contacto, horasAlerta));
    }
}
