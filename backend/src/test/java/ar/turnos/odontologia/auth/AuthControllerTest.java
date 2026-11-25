package ar.turnos.odontologia.auth;

import static org.hamcrest.Matchers.matchesPattern;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import ar.turnos.odontologia.common.ApiErrorFactory;
import ar.turnos.odontologia.common.GlobalExceptionHandler;
import ar.turnos.odontologia.config.AppPropertiesConfig;
import ar.turnos.odontologia.config.ClockConfig;
import ar.turnos.odontologia.config.SecurityConfig;
import ar.turnos.odontologia.security.ApiAuthenticationEntryPoint;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import({ SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiErrorFactory.class,
        GlobalExceptionHandler.class, ClockConfig.class, AppPropertiesConfig.class, AuthService.class,
        JwtService.class, OdontologoUserDetailsService.class })
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwt;

    @MockitoBean
    private AuthenticationManager manager;

    @MockitoBean
    private OdontologoRepository odontologos;

    @Test
    void loginValidoDevuelveTokenBearerYExpiraEnConOffset() throws Exception {
        when(manager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(principal(), null, List.of()));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"  Odontologo@Ejemplo.com  \",\"password\":\"clave-ficticia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", matchesPattern("^[A-Za-z0-9._-]+$")))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn", matchesPattern(".*-03:00$")))
                .andExpect(jsonPath("$.nombre").value("Odontólogo Ejemplo"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void emailVacioDevuelve400Validacion() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"password\":\"clave-ficticia\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[0].campo").value("email"));
    }

    @Test
    void jsonIlegibleDevuelve400SolicitudMalFormada() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{ mal escrito"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_MAL_FORMADA"));
    }

    @Test
    void credencialesInvalidasDanElMismoMensajeParaEmailInexistenteYPasswordIncorrecta() throws Exception {
        when(manager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"))
                .thenThrow(new UsernameNotFoundException("No se encontró el odontólogo."));

        String porPassword = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"odontologo@ejemplo.com\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.mensaje").value("Email o contraseña incorrectos."))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String porEmail = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"inexistente@ejemplo.com\",\"password\":\"cualquiera\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.mensaje").value("Email o contraseña incorrectos."))
                .andReturn().getResponse().getContentAsString();

        assertThat(porEmail).contains("\"mensaje\":\"Email o contraseña incorrectos.\"");
        assertThat(porPassword).contains("\"mensaje\":\"Email o contraseña incorrectos.\"");
    }

    @Test
    void meConTokenValidoDevuelveIdNombreYEmailDelContextoSinConsultarDeNuevo() throws Exception {
        Odontologo odontologo = new Odontologo("Odontólogo Ejemplo", "odontologo@ejemplo.com", "hash-bcrypt-ficticio");
        ReflectionTestUtils.setField(odontologo, "id", 7L);
        when(odontologos.findByEmail("odontologo@ejemplo.com")).thenReturn(Optional.of(odontologo));
        String token = jwt.emitir("  Odontologo@Ejemplo.com  ").token();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.nombre").value("Odontólogo Ejemplo"))
                .andExpect(jsonPath("$.email").value("odontologo@ejemplo.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(odontologos).findByEmail("odontologo@ejemplo.com");
    }

    @Test
    void meSinTokenDevuelve401NoAutenticado() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    private OdontologoPrincipal principal() {
        Odontologo odontologo = new Odontologo("Odontólogo Ejemplo", "odontologo@ejemplo.com", "hash-bcrypt-ficticio");
        ReflectionTestUtils.setField(odontologo, "id", 7L);
        return new OdontologoPrincipal(odontologo);
    }
}
