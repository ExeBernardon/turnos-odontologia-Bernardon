package ar.turnos.odontologia.turno;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** Agenda del odontólogo y acciones sobre turnos; requiere JWT (SecurityConfig). */
@RestController
@RequestMapping("/api/turnos")
public class TurnoController {

    private final TurnoService service;

    public TurnoController(TurnoService service) {
        this.service = service;
    }

    @GetMapping
    public List<TurnoResponse> listar(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) String estado) {
        return service.listarAgenda(desde, hasta, estado);
    }

    @GetMapping("/{id}")
    public TurnoResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<TurnoResponse> crear(@Valid @RequestBody TurnoManualRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearManual(request));
    }

    @PostMapping("/{id}/confirmar")
    public TurnoResponse confirmar(@PathVariable Long id) {
        return service.confirmar(id);
    }

    @PostMapping("/{id}/cancelar")
    public TurnoResponse cancelar(@PathVariable Long id) {
        return service.cancelar(id);
    }

    @PatchMapping("/{id}/estado")
    public TurnoResponse marcarAsistencia(@PathVariable Long id,
            @Valid @RequestBody EstadoAsistenciaRequest request) {
        return service.marcarAsistencia(id, request.estado());
    }

    @PatchMapping("/{id}/reprogramar")
    public TurnoResponse reprogramar(@PathVariable Long id,
            @Valid @RequestBody ReprogramarRequest request) {
        return service.reprogramar(id, request.inicio().toInstant());
    }
}
