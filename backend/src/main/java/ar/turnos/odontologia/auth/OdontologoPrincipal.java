package ar.turnos.odontologia.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class OdontologoPrincipal implements UserDetails {

    private final Long id;
    private final String nombre;
    private final String email;
    private final String passwordHash;

    public OdontologoPrincipal(Odontologo odontologo) {
        this.id = odontologo.getId();
        this.nombre = odontologo.getNombre();
        this.email = odontologo.getEmail();
        this.passwordHash = odontologo.getPasswordHash();
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
