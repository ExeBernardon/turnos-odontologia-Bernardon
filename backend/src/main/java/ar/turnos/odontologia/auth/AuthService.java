package ar.turnos.odontologia.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import ar.turnos.odontologia.common.ApiException;

@Service
public class AuthService {

    public static final String MSG_CREDENCIALES_INVALIDAS = "Email o contraseña incorrectos.";

    private final AuthenticationManager authenticationManager;

    public AuthService(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    public OdontologoPrincipal autenticar(String email, String password) {
        try {
            Authentication autenticado = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
            return (OdontologoPrincipal) autenticado.getPrincipal();
        } catch (AuthenticationException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", MSG_CREDENCIALES_INVALIDAS);
        }
    }
}
