package ar.turnos.odontologia.bloqueo;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BloqueoRepository extends JpaRepository<Bloqueo, Long> {

    /** Bloqueos cuyo rango [inicio, fin) interseca [desde, hasta); los que solo tocan un borde no entran. */
    @Query("select b from Bloqueo b where b.inicio < :hasta and b.fin > :desde order by b.inicio")
    List<Bloqueo> findQueIntersecan(@Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
