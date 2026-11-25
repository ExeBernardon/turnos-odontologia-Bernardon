package ar.turnos.odontologia.prestacion;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.turnos.odontologia.common.ApiException;

@Service
@Transactional(readOnly = true)
public class PrestacionService {

    static final String MSG_NO_ENCONTRADO = "No encontramos el recurso solicitado.";
    static final String MSG_CON_TURNOS = "La prestación tiene turnos y no se puede eliminar. Desactivala.";

    private final PrestacionRepository prestaciones;

    public PrestacionService(PrestacionRepository prestaciones) {
        this.prestaciones = prestaciones;
    }

    public List<PrestacionResponse> listar() {
        return prestaciones.findAllByOrderByIdAsc().stream().map(PrestacionMapper::aRespuesta).toList();
    }

    public List<PrestacionPublicaResponse> listarActivas() {
        return prestaciones.findByActivaTrueOrderByIdAsc().stream().map(PrestacionMapper::aPublica).toList();
    }

    @Transactional
    public PrestacionResponse crear(PrestacionRequest req) {
        Prestacion p = new Prestacion(req.nombre().trim(), req.duracionMinutos());
        p.setActiva(activaOPorDefecto(req));
        return PrestacionMapper.aRespuesta(prestaciones.save(p));
    }

    @Transactional
    public PrestacionResponse reemplazar(Long id, PrestacionRequest req) {
        Prestacion p = buscar(id);
        p.setNombre(req.nombre().trim());
        p.setDuracionMinutos(req.duracionMinutos());
        p.setActiva(activaOPorDefecto(req));
        return PrestacionMapper.aRespuesta(prestaciones.save(p));
    }

    @Transactional
    public void eliminar(Long id) {
        Prestacion p = buscar(id);
        if (prestaciones.tieneTurnos(id)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRESTACION_CON_TURNOS", MSG_CON_TURNOS);
        }
        prestaciones.delete(p);
    }

    private Prestacion buscar(Long id) {
        return prestaciones.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", MSG_NO_ENCONTRADO));
    }

    private static boolean activaOPorDefecto(PrestacionRequest req) {
        return req.activa() == null || req.activa();
    }
}
