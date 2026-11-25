package ar.turnos.odontologia;

import java.time.ZoneId;

import ar.turnos.odontologia.config.AppProperties;

/** Configuracion ficticia y determinista para tests unitarios (sin contenedores). */
public final class AppPropertiesFixture {

    public static final String CONTACTO = "Consultorio de ejemplo - Tel. +54 9 11 0000-0000";

    private AppPropertiesFixture() {
    }

    public static AppProperties crear() {
        return new AppProperties(
                ZoneId.of("America/Argentina/Buenos_Aires"),
                "http://localhost:8080",
                new AppProperties.Agenda(15),
                new AppProperties.Recordatorio(15, 48),
                new AppProperties.Alerta(24),
                new AppProperties.Cancelacion(24),
                new AppProperties.Turno(2, 60),
                new AppProperties.Consultorio(CONTACTO),
                new AppProperties.Mail("turnos@ejemplo.local"),
                new AppProperties.Jwt("secreto-de-prueba-ficticio-con-longitud-suficiente-1234567890", 480),
                new AppProperties.Seed(new AppProperties.Seed.Odontologo(
                        "Dr. Ejemplo", "odontologo@ejemplo.com", "clave-ficticia")));
    }
}
