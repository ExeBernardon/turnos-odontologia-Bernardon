package ar.turnos.odontologia.bloqueo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bloqueos")
public class BloqueoController {

    private final BloqueoService service;

    public BloqueoController(BloqueoService service) {
        this.service = service;
    }

    /** Parametros opcionales a nivel Spring: la ausencia es 400 VALIDACION (contrato), no SOLICITUD_MAL_FORMADA. */
    @GetMapping
    public List<BloqueoResponse> listar(@RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {
        return service.listar(desde, hasta);
    }

    @PostMapping("/previsualizar")
    public BloqueoPrevisualizacionResponse previsualizar(@Valid @RequestBody BloqueoRequest req) {
        return service.previsualizar(req);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueoCreadoResponse crear(@Valid @RequestBody BloqueoCrearRequest req) {
        return service.crear(req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }
}
