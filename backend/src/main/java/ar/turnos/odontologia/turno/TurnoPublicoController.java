package ar.turnos.odontologia.turno;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** Endpoints publicos de reserva y consulta por token. Sin JWT. */
@RestController
@RequestMapping("/api/public/turnos")
public class TurnoPublicoController {

    private final TurnoService service;

    public TurnoPublicoController(TurnoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TurnoPublicoResponse> reservar(@Valid @RequestBody ReservaPublicaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reservar(request));
    }

    @GetMapping("/{token}")
    public TurnoPublicoResponse consultar(@PathVariable String token) {
        return service.consultarPublico(token);
    }

    @PostMapping("/{token}/confirmar")
    public TurnoPublicoResponse confirmar(@PathVariable String token) {
        return service.confirmarPublico(token);
    }

    @PostMapping("/{token}/cancelar")
    public TurnoPublicoResponse cancelar(@PathVariable String token) {
        return service.cancelarPublico(token);
    }
}
