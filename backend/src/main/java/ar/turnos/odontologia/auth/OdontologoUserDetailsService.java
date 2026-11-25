package ar.turnos.odontologia.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.turnos.odontologia.common.EmailNormalizador;

@Service
public class OdontologoUserDetailsService implements UserDetailsService {

    private final OdontologoRepository odontologos;

    public OdontologoUserDetailsService(OdontologoRepository odontologos) {
        this.odontologos = odontologos;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return odontologos.findByEmail(EmailNormalizador.normalizar(email))
                .map(OdontologoPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No se encontró el odontólogo."));
    }
}
