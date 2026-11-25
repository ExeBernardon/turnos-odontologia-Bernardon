package ar.turnos.odontologia.paciente;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    Optional<Paciente> findByDni(String dni);

    boolean existsByDni(String dni);

    boolean existsByDniAndIdNot(String dni, Long id);

    List<Paciente> findAllByOrderByApellidoAscNombreAscIdAsc();

    /** Pacientes cuyo DNI empieza con {@code prefijo} (literal), ordenados por apellido, nombre e id. */
    default List<Paciente> buscarPorPrefijoDni(String prefijo) {
        return porDniLike(PrefijoLike.escapar(prefijo) + "%");
    }

    /** Pacientes cuyo apellido empieza con {@code prefijo} (literal, sin distinguir mayusculas). */
    default List<Paciente> buscarPorPrefijoApellido(String prefijo) {
        return porApellidoLike(PrefijoLike.escapar(prefijo) + "%");
    }

    @Query(value = "select * from paciente where dni like :patron escape '\\' order by apellido, nombre, id",
            nativeQuery = true)
    List<Paciente> porDniLike(@Param("patron") String patron);

    @Query(value = "select * from paciente where lower(apellido) like lower(:patron) escape '\\' "
            + "order by apellido, nombre, id", nativeQuery = true)
    List<Paciente> porApellidoLike(@Param("patron") String patron);

    /** Contacto del turno creado mas recientemente (cualquier estado) de cada paciente; sin N+1. */
    @Query(value = "select distinct on (paciente_id) paciente_id as pacienteId, email_contacto as email, "
            + "telefono_contacto as telefono from turno where paciente_id in (:ids) "
            + "order by paciente_id, creado_en desc, id desc", nativeQuery = true)
    List<ContactoUltimoTurno> contactosDelUltimoTurno(@Param("ids") Collection<Long> ids);
}
