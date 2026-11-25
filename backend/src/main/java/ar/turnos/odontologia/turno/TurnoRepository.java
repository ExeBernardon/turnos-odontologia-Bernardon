package ar.turnos.odontologia.turno;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

    Optional<Turno> findByToken(String token);

    /** Agenda: todos los estados con inicio en [desde, hasta), ordenados por horario. */
    List<Turno> findByInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(Instant desde, Instant hasta);

    /** Turnos activos cuyo rango [inicio, fin) interseca [desde, hasta); los que solo tocan un borde no entran. */
    default List<Turno> findActivosQueIntersecan(Instant desde, Instant hasta) {
        return findPorEstadosQueIntersecan(EstadoTurno.ACTIVOS, desde, hasta);
    }

    @Query("select t from Turno t where t.estado in :estados and t.inicio < :hasta and t.fin > :desde "
            + "order by t.inicio")
    List<Turno> findPorEstadosQueIntersecan(@Param("estados") Set<EstadoTurno> estados,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
