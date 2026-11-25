package ar.turnos.odontologia.turno;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** Carga manual de turnos desde la agenda; requiere JWT (SecurityConfig). */
@RestController
@RequestMapping("/api/turnos")
public class TurnoController {

    private final TurnoService service;

    public TurnoController(TurnoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TurnoResponse> crear(@Valid @RequestBody TurnoManualRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearManual(request));
    }
}
