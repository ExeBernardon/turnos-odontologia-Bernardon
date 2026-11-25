package ar.turnos.odontologia.config;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;

import ar.turnos.odontologia.common.ApiErrorFactory;
import ar.turnos.odontologia.common.GlobalExceptionHandler;
import ar.turnos.odontologia.security.ApiAuthenticationEntryPoint;

/** Reglas de acceso del contrato: sin token solo login y /api/public/** pasan; el resto es 401 uniforme. */
@WebMvcTest(excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = RestController.class))
@ActiveProfiles("test")
@Import({ SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiErrorFactory.class,
        GlobalExceptionHandler.class, ClockConfig.class, AppPropertiesConfig.class })
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void rutaPrivadaSinTokenDevuelve401UniformeNoAutenticado() throws Exception {
        mvc.perform(get("/api/turnos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value("Tenés que iniciar sesión para continuar."))
                .andExpect(jsonPath("$.path").value("/api/turnos"))
                .andExpect(jsonPath("$.timestamp", matchesPattern(".*-03:00$")))
                .andExpect(jsonPath("$.detalles").doesNotExist());
    }

    @Test
    void authMeSinTokenTambienEs401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
    }

    @Test
    void escrituraPrivadaSinTokenEs401NoCsrf403() throws Exception {
        mvc.perform(post("/api/prestaciones").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void rutaPublicaSinTokenNoEs401() throws Exception {
        mvc.perform(get("/api/public/prestaciones"))
                .andExpect(status().is(not(401)));
    }

    @Test
    void loginSinTokenNoEs401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(not(401)))
                .andExpect(status().is(not(403)));
    }

    @Test
    void noEmiteCookieDeSesion() throws Exception {
        mvc.perform(get("/api/turnos"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get("/api/public/prestaciones"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
