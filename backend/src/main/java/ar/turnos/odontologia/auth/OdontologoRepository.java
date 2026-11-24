package ar.turnos.odontologia.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OdontologoRepository extends JpaRepository<Odontologo, Long> {

    Optional<Odontologo> findByEmail(String email);
}
