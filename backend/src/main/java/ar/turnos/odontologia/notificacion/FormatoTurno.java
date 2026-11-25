package ar.turnos.odontologia.notificacion;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Muestra el inicio de un turno en la zona del consultorio y en espanol (ej. "jueves 10 de diciembre de 2026, 10:00"). */
public class FormatoTurno {

    private final DateTimeFormatter formato;

    public FormatoTurno(ZoneId zona) {
        this.formato = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-AR"))
                .withZone(zona);
    }

    public String fechaHora(Instant inicio) {
        return formato.format(inicio);
    }
}
