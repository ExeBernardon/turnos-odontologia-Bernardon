package ar.turnos.odontologia.notificacion;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.turno.EstadoTurno;
import ar.turnos.odontologia.turno.Turno;

import java.time.Instant;

/** Apoyo de los tests de notificacion: motor Thymeleaf sin Spring y un turno ficticio en memoria. */
final class MotorPlantillasPrueba {

    static final String TOKEN = "tokFicticio-123_abc";
    static final String EMAIL_PACIENTE = "paciente@ejemplo.com";
    static final String TELEFONO = "1155555555";

    private MotorPlantillasPrueba() {
    }

    static TemplateEngine motor() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        TemplateEngine motor = new SpringTemplateEngine();
        motor.setTemplateResolver(resolver);
        return motor;
    }

    static Turno turno(String nombre, String token) {
        return new Turno(new Paciente(nombre, "Pérez", "99999999"), new Prestacion("Limpieza dental", 30),
                Instant.parse("2026-12-10T13:00:00Z"), Instant.parse("2026-12-10T13:30:00Z"),
                EstadoTurno.PENDIENTE, token, EMAIL_PACIENTE, TELEFONO);
    }
}
