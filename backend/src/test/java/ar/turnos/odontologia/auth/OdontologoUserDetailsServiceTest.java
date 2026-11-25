package ar.turnos.odontologia.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

class OdontologoUserDetailsServiceTest {

    private final OdontologoRepository repository = mock(OdontologoRepository.class);
    private final OdontologoUserDetailsService service = new OdontologoUserDetailsService(repository);

    @Test
    void encuentraAlOdontologoConVariantesDeEspaciosYMayusculas() {
        Odontologo odontologo = new Odontologo("Odontólogo Ejemplo", "odontologo@ejemplo.com",
                "hash-bcrypt-ficticio");
        ReflectionTestUtils.setField(odontologo, "id", 7L);
        when(repository.findByEmail("odontologo@ejemplo.com")).thenReturn(Optional.of(odontologo));

        UserDetails cargado = service.loadUserByUsername("  Odontologo@Ejemplo.com  ");

        assertThat(cargado).isInstanceOf(OdontologoPrincipal.class);
        assertThat(cargado.getUsername()).isEqualTo("odontologo@ejemplo.com");
        assertThat(cargado.getPassword()).isEqualTo("hash-bcrypt-ficticio");
        assertThat(((OdontologoPrincipal) cargado).getId()).isEqualTo(7L);
        verify(repository).findByEmail("odontologo@ejemplo.com");
    }

    @Test
    void emailInexistenteLanzaUsernameNotFoundExceptionSinRevelarElEmail() {
        when(repository.findByEmail("inexistente@ejemplo.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("  Inexistente@Ejemplo.com  "))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("No se encontró el odontólogo.");
    }
}
