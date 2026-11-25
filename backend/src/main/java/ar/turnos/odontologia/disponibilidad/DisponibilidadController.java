package ar.turnos.odontologia.disponibilidad;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Horarios libres para el odontólogo: requiere JWT y solo excluye el pasado (sin ventana de 2 h). */
@RestController
@RequestMapping("/api/disponibilidad")
public class DisponibilidadController {

    private final DisponibilidadService service;

    public DisponibilidadController(DisponibilidadService service) {
        this.service = service;
    }

    @GetMapping
    public DisponibilidadResponse consultar(@RequestParam(required = false) Long prestacionId,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {
        return service.consultar(prestacionId, desde, hasta, ModoDisponibilidad.ODONTOLOGO);
    }
}
