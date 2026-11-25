package ar.turnos.odontologia.prestacion;

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
@RequestMapping("/api/prestaciones")
public class PrestacionController {

    private final PrestacionService service;

    public PrestacionController(PrestacionService service) {
        this.service = service;
    }

    @GetMapping
    public List<PrestacionResponse> listar() {
        return service.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrestacionResponse crear(@Valid @RequestBody PrestacionRequest req) {
        return service.crear(req);
    }

    @PutMapping("/{id}")
    public PrestacionResponse reemplazar(@PathVariable Long id, @Valid @RequestBody PrestacionRequest req) {
        return service.reemplazar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }
}
