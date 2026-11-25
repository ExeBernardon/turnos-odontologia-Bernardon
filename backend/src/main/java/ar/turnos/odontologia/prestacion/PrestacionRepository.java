package ar.turnos.odontologia.prestacion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrestacionRepository extends JpaRepository<Prestacion, Long> {

    List<Prestacion> findAllByOrderByIdAsc();

    List<Prestacion> findByActivaTrueOrderByIdAsc();

    /** True si existe al menos un turno (en cualquier estado) de la prestacion. */
    @Query("select count(t) > 0 from Turno t where t.prestacion.id = :id")
    boolean tieneTurnos(@Param("id") Long id);
}
