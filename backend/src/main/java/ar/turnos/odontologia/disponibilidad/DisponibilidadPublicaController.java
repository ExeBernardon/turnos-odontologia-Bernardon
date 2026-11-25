package ar.turnos.odontologia.disponibilidad;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Horarios libres para el paciente: sin JWT y con la ventana pública de 2 h a 60 días. */
@RestController
@RequestMapping("/api/public/disponibilidad")
public class DisponibilidadPublicaController {

    private final DisponibilidadService service;

    public DisponibilidadPublicaController(DisponibilidadService service) {
        this.service = service;
    }

    /** Parámetros opcionales a nivel Spring: la ausencia se valida en el service como 400 VALIDACION. */
    @GetMapping
    public DisponibilidadResponse consultar(@RequestParam(required = false) Long prestacionId,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {
        return service.consultar(prestacionId, desde, hasta, ModoDisponibilidad.PUBLICA);
    }
}
