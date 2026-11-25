package ar.turnos.odontologia.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import ar.turnos.odontologia.common.ApiException;

class AuthServiceTest {

    private final AuthenticationManager manager = mock(AuthenticationManager.class);
    private final AuthService service = new AuthService(manager);

    @Test
    void credencialesValidasDevuelvenElPrincipalDelOdontologo() {
        OdontologoPrincipal principal = new OdontologoPrincipal(
                new Odontologo("Odontólogo Ejemplo", "odontologo@ejemplo.com", "hash-bcrypt-ficticio"));
        Authentication autenticado = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        when(manager.authenticate(any(Authentication.class))).thenReturn(autenticado);

        assertThat(service.autenticar("  Odontologo@Ejemplo.com  ", "clave-ficticia")).isSameAs(principal);
    }

    @Test
    void contraseñaIncorrectaYEmaInexistenteTienenElMismoCodigoYMensaje() {
        when(manager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"))
                .thenThrow(new UsernameNotFoundException("No se encontró el odontólogo."));

        ApiException porContraseña = catchApiException(() -> service.autenticar("odontologo@ejemplo.com", "mala"));
        ApiException porEmail = catchApiException(() -> service.autenticar("inexistente@ejemplo.com", "cualquiera"));

        assertThat(porContraseña.getStatus().value()).isEqualTo(401);
        assertThat(porEmail.getStatus().value()).isEqualTo(401);
        assertThat(porContraseña.getCodigo()).isEqualTo("CREDENCIALES_INVALIDAS");
        assertThat(porEmail.getCodigo()).isEqualTo(porContraseña.getCodigo());
        assertThat(porEmail.getMessage()).isEqualTo(porContraseña.getMessage());
        assertThat(porContraseña.getMessage()).isEqualTo("Email o contraseña incorrectos.");
    }

    private ApiException catchApiException(ThrowingCallable accion) {
        return catchThrowableOfType(accion, ApiException.class);
    }
}
