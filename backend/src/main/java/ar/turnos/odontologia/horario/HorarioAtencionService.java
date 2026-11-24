package ar.turnos.odontologia.horario;

import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.turnos.odontologia.common.ApiException;

@Service
@Transactional(readOnly = true)
public class HorarioAtencionService {

    static final String MSG_NO_ENCONTRADO = "No encontramos el recurso solicitado.";
    static final String MSG_RANGO_INVALIDO = "La hora de inicio debe ser anterior a la de fin.";
    static final String MSG_SOLAPADOS = "El rango se superpone con otro horario de atención del mismo día.";

    private final HorarioAtencionRepository horarios;

    public HorarioAtencionService(HorarioAtencionRepository horarios) {
        this.horarios = horarios;
    }

    public List<HorarioAtencionResponse> listar() {
        return horarios.findAll().stream()
                .sorted(Comparator.comparing(HorarioAtencion::getDiaSemana)
                        .thenComparing(HorarioAtencion::getHoraInicio))
                .map(HorarioAtencionMapper::aRespuesta)
                .toList();
    }

    @Transactional
    public HorarioAtencionResponse crear(HorarioAtencionRequest req) {
        DayOfWeek dia = validar(req, null);
        return HorarioAtencionMapper.aRespuesta(
                horarios.save(new HorarioAtencion(dia, req.horaInicio(), req.horaFin())));
    }

    @Transactional
    public HorarioAtencionResponse reemplazar(Long id, HorarioAtencionRequest req) {
        HorarioAtencion h = buscar(id);
        DayOfWeek dia = validar(req, id);
        h.setDiaSemana(dia);
        h.setHoraInicio(req.horaInicio());
        h.setHoraFin(req.horaFin());
        return HorarioAtencionMapper.aRespuesta(horarios.save(h));
    }

    @Transactional
    public void eliminar(Long id) {
        horarios.delete(buscar(id));
    }

    /** Valida inicio &lt; fin y que no se solape (rangos semiabiertos) con otro del dia; ignora {@code propioId}. */
    private DayOfWeek validar(HorarioAtencionRequest req, Long propioId) {
        if (!req.horaInicio().isBefore(req.horaFin())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "RANGO_HORARIO_INVALIDO", MSG_RANGO_INVALIDO);
        }
        DayOfWeek dia = DayOfWeek.of(req.diaSemana());
        boolean solapa = horarios.findByDiaSemanaOrderByHoraInicio(dia).stream()
                .filter(o -> !o.getId().equals(propioId))
                .anyMatch(o -> req.horaInicio().isBefore(o.getHoraFin()) && o.getHoraInicio().isBefore(req.horaFin()));
        if (solapa) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "HORARIOS_SOLAPADOS", MSG_SOLAPADOS);
        }
        return dia;
    }

    private HorarioAtencion buscar(Long id) {
        return horarios.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", MSG_NO_ENCONTRADO));
    }
}
