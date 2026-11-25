package ar.turnos.odontologia.paciente;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.turnos.odontologia.common.ApiException;
import ar.turnos.odontologia.turno.NormalizacionDni;

@Service
@Transactional(readOnly = true)
public class PacienteService {

    static final String MSG_NO_ENCONTRADO = "No encontramos el recurso solicitado.";
    static final String MSG_DNI_DUPLICADO = "Ya existe un paciente con ese DNI.";

    private final PacienteRepository pacientes;

    public PacienteService(PacienteRepository pacientes) {
        this.pacientes = pacientes;
    }

    /** Sin q: todos. Solo digitos (ignorando puntos y espacios): prefijo de DNI. Si no: prefijo de apellido. */
    public List<PacienteResponse> buscar(String q) {
        return conContacto(buscarEntidades(q == null ? "" : q.trim()));
    }

    public PacienteResponse obtener(Long id) {
        return conContacto(List.of(buscarPorId(id))).get(0);
    }

    @Transactional
    public PacienteResponse crear(PacienteRequest req) {
        String dni = NormalizacionDni.normalizar(req.dni());
        if (pacientes.existsByDni(dni)) {
            throw dniDuplicado();
        }
        Paciente p = new Paciente(req.nombre().trim(), req.apellido().trim(), dni);
        return guardar(p);
    }

    @Transactional
    public PacienteResponse reemplazar(Long id, PacienteRequest req) {
        Paciente p = buscarPorId(id);
        String dni = NormalizacionDni.normalizar(req.dni());
        if (pacientes.existsByDniAndIdNot(dni, id)) {
            throw dniDuplicado();
        }
        p.setNombre(req.nombre().trim());
        p.setApellido(req.apellido().trim());
        p.setDni(dni);
        return guardar(p);
    }

    private PacienteResponse guardar(Paciente p) {
        try {
            return conContacto(List.of(pacientes.saveAndFlush(p))).get(0);
        } catch (DataIntegrityViolationException e) {
            if (ViolacionDeDniUnico.es(e)) {
                throw dniDuplicado();
            }
            throw e;
        }
    }

    private List<Paciente> buscarEntidades(String q) {
        if (q.isEmpty()) {
            return pacientes.findAllByOrderByApellidoAscNombreAscIdAsc();
        }
        String sinPuntos = q.replace(".", "").replace(" ", "");
        if (sinPuntos.matches("[0-9]+")) {
            return pacientes.buscarPorPrefijoDni(sinPuntos);
        }
        return pacientes.buscarPorPrefijoApellido(q);
    }

    /** Una sola consulta extra para los contactos de todos los pacientes (sin N+1). */
    private List<PacienteResponse> conContacto(List<Paciente> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<Long, ContactoResponse> contactos = pacientes
                .contactosDelUltimoTurno(lista.stream().map(Paciente::getId).toList()).stream()
                .collect(Collectors.toMap(ContactoUltimoTurno::getPacienteId,
                        c -> new ContactoResponse(c.getEmail(), c.getTelefono()), (a, b) -> a));
        return lista.stream().map(p -> PacienteMapper.aRespuesta(p, contactos.get(p.getId()))).toList();
    }

    private Paciente buscarPorId(Long id) {
        return pacientes.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", MSG_NO_ENCONTRADO));
    }

    private static ApiException dniDuplicado() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DNI_DUPLICADO", MSG_DNI_DUPLICADO);
    }
}
