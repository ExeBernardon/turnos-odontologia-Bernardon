package ar.turnos.odontologia.common;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ar.turnos.odontologia.config.AppPropertiesConfig;
import ar.turnos.odontologia.config.ClockConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@WebMvcTest(GlobalExceptionHandlerTest.ControladorDePrueba.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({ GlobalExceptionHandler.class, ApiErrorFactory.class, ClockConfig.class, AppPropertiesConfig.class,
        GlobalExceptionHandlerTest.ControladorDePrueba.class })
class GlobalExceptionHandlerTest {

    record Cuerpo(@NotBlank String nombre, @NotBlank String email) {
    }

    @RestController
    @Validated
    static class ControladorDePrueba {

        @PostMapping("/prueba/validar")
        void validar(@Valid @RequestBody Cuerpo cuerpo) {
        }

        @GetMapping("/prueba/numero/{n}")
        void numero(@PathVariable int n) {
        }

        @GetMapping("/prueba/transicion")
        void transicion() {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TRANSICION_INVALIDA",
                    "Ese cambio de estado no está permitido para este turno.");
        }

        @GetMapping("/prueba/solapamiento")
        void solapamiento() {
            throw new DataIntegrityViolationException(
                    "could not execute statement [ERROR: conflicting key value violates exclusion constraint "
                            + "\"turno_sin_solapamiento\"]",
                    new java.sql.SQLException("ERROR: conflicting key value violates exclusion constraint", "23P01"));
        }

        @GetMapping("/prueba/solapamiento-en-commit")
        void solapamientoEnCommit() {
            throw new TransactionSystemException("Could not commit JPA transaction",
                    new RuntimeException(new java.sql.SQLException("exclusion", "23P01")));
        }

        @GetMapping("/prueba/unicidad")
        void unicidad() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint \"paciente_dni_uk\"",
                    new java.sql.SQLException("duplicate key", "23505"));
        }

        @GetMapping("/prueba/boom")
        void boom() {
            throw new IllegalStateException("ERROR SQL interno: relation turno does not exist");
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void validacionDevuelve400ConDetallesPorCampo() throws Exception {
        mvc.perform(post("/prueba/validar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"\",\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value("Hay campos con errores."))
                .andExpect(jsonPath("$.path").value("/prueba/validar"))
                .andExpect(jsonPath("$.timestamp", matchesPattern(".*-03:00$")))
                .andExpect(jsonPath("$.detalles.length()").value(2))
                .andExpect(jsonPath("$.detalles[?(@.campo=='nombre')]").exists())
                .andExpect(jsonPath("$.detalles[?(@.campo=='email')]").exists());
    }

    @Test
    void jsonIlegibleDevuelve400SolicitudMalFormadaSinDetalles() throws Exception {
        mvc.perform(post("/prueba/validar").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_MAL_FORMADA"))
                .andExpect(jsonPath("$.detalles").doesNotExist());
    }

    @Test
    void parametroDeRutaNoParseableDevuelve400SolicitudMalFormada() throws Exception {
        mvc.perform(get("/prueba/numero/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_MAL_FORMADA"))
                .andExpect(jsonPath("$.detalles").doesNotExist());
    }

    @Test
    void rutaInexistenteDevuelve404RecursoNoEncontrado() throws Exception {
        mvc.perform(get("/prueba/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("No encontramos el recurso solicitado."))
                .andExpect(jsonPath("$.path").value("/prueba/no-existe"));
    }

    @Test
    void apiExceptionDevuelveSuStatusYCodigoSinDetalles() throws Exception {
        mvc.perform(get("/prueba/transicion"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value("Ese cambio de estado no está permitido para este turno."))
                .andExpect(jsonPath("$.detalles").doesNotExist());
    }

    @Test
    void metodoNoSoportadoNoSeConvierteEn500() throws Exception {
        mvc.perform(post("/prueba/transicion"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void excepcionInesperadaDevuelve500GenericoSinFiltrarDetalles() throws Exception {
        mvc.perform(get("/prueba/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.mensaje").value("Ocurrió un error inesperado. Intentá de nuevo en unos minutos."))
                .andExpect(jsonPath("$.path").value("/prueba/boom"))
                .andExpect(jsonPath("$.detalles").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("relation turno"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("IllegalStateException"))));
    }

    @Test
    void violacionDeSolapamientoDevuelve409HorarioOcupadoSinFiltrarLaBase() throws Exception {
        mvc.perform(get("/prueba/solapamiento"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.codigo").value("HORARIO_OCUPADO"))
                .andExpect(jsonPath("$.mensaje").value("Ese horario ya fue reservado. Elegí otro."))
                .andExpect(jsonPath("$.path").value("/prueba/solapamiento"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.detalles").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("turno_sin_solapamiento"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("exclusion"))));
    }

    @Test
    void solapamientoEnvueltoEnOtraExcepcionTambienDevuelve409() throws Exception {
        mvc.perform(get("/prueba/solapamiento-en-commit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HORARIO_OCUPADO"));
    }

    @Test
    void otraViolacionDeIntegridadDevuelve500ErrorInterno() throws Exception {
        mvc.perform(get("/prueba/unicidad"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("paciente_dni_uk"))));
    }
}
