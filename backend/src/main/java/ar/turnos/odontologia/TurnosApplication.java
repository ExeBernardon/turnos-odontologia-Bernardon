package ar.turnos.odontologia;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TurnosApplication {

    public static void main(String[] args) {
        // Los instantes se manejan en UTC sin importar el huso de la maquina (AGENTS.md regla 4).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(TurnosApplication.class, args);
    }
}
