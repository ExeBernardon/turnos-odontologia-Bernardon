package ar.turnos.odontologia.prestacion;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catalogo para la reserva publica: solo prestaciones activas. */
@RestController
@RequestMapping("/api/public/prestaciones")
public class PrestacionPublicaController {

    private final PrestacionService service;

    public PrestacionPublicaController(PrestacionService service) {
        this.service = service;
    }

    @GetMapping
    public List<PrestacionPublicaResponse> listarActivas() {
        return service.listarActivas();
    }
}
