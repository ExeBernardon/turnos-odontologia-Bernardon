package ar.turnos.odontologia.notificacion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import ar.turnos.odontologia.turno.Turno;

/**
 * Consultas del job de avisos programados (C-12) sobre la entidad {@link Turno}. Vive en este paquete y no en
 * {@code TurnoRepository} para no tocar el dominio de turnos. La seleccion usa el indice
 * {@code turno_estado_inicio_idx (estado, inicio)}.
 */
public interface TurnoAvisosRepository extends Repository<Turno, Long> {

    Optional<Turno> findById(Long id);

    /** Ids PENDIENTE sin recordatorio con inicio en (ahora, hasta]; el borde de {@code hasta} entra. */
    @Query("select t.id from Turno t where t.estado = ar.turnos.odontologia.turno.EstadoTurno.PENDIENTE "
            + "and t.recordatorioEnviado = false and t.inicio > :ahora and t.inicio <= :hasta order by t.inicio")
    List<Long> idsParaRecordatorio(@Param("ahora") Instant ahora, @Param("hasta") Instant hasta);

    /** Ids PENDIENTE sin alerta con inicio en (ahora, hasta); el borde de {@code hasta} no entra. */
    @Query("select t.id from Turno t where t.estado = ar.turnos.odontologia.turno.EstadoTurno.PENDIENTE "
            + "and t.alertaPendienteEnviada = false and t.inicio > :ahora and t.inicio < :hasta order by t.inicio")
    List<Long> idsParaAlerta(@Param("ahora") Instant ahora, @Param("hasta") Instant hasta);

    /**
     * Marca el recordatorio solo si el turno sigue PENDIENTE con el mismo inicio que se aviso, sin tocar
     * estado, inicio ni el otro flag. Devuelve las filas afectadas (0 si el turno cambio mientras tanto).
     */
    @Transactional
    @Modifying
    @Query("update Turno t set t.recordatorioEnviado = true where t.id = :id "
            + "and t.estado = ar.turnos.odontologia.turno.EstadoTurno.PENDIENTE "
            + "and t.inicio = :inicio and t.recordatorioEnviado = false")
    int marcarRecordatorioEnviado(@Param("id") Long id, @Param("inicio") Instant inicio);

    /** Idem {@link #marcarRecordatorioEnviado} para {@code alerta_pendiente_enviada}. */
    @Transactional
    @Modifying
    @Query("update Turno t set t.alertaPendienteEnviada = true where t.id = :id "
            + "and t.estado = ar.turnos.odontologia.turno.EstadoTurno.PENDIENTE "
            + "and t.inicio = :inicio and t.alertaPendienteEnviada = false")
    int marcarAlertaEnviada(@Param("id") Long id, @Param("inicio") Instant inicio);
}
