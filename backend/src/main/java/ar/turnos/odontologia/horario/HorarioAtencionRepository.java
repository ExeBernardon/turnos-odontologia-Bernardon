package ar.turnos.odontologia.horario;

import java.time.DayOfWeek;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HorarioAtencionRepository extends JpaRepository<HorarioAtencion, Long> {

    List<HorarioAtencion> findByDiaSemanaOrderByHoraInicio(DayOfWeek diaSemana);
}
