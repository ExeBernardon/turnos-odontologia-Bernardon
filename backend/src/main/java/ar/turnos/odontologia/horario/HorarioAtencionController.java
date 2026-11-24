package ar.turnos.odontologia.horario;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/horarios-atencion")
public class HorarioAtencionController {

    private final HorarioAtencionService service;

    public HorarioAtencionController(HorarioAtencionService service) {
        this.service = service;
    }

    @GetMapping
    public List<HorarioAtencionResponse> listar() {
        return service.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HorarioAtencionResponse crear(@Valid @RequestBody HorarioAtencionRequest req) {
        return service.crear(req);
    }

    @PutMapping("/{id}")
    public HorarioAtencionResponse reemplazar(@PathVariable Long id, @Valid @RequestBody HorarioAtencionRequest req) {
        return service.reemplazar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }
}
