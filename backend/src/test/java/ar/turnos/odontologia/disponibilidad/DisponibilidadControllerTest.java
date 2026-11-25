package ar.turnos.odontologia.disponibilidad;

import static ar.turnos.odontologia.disponibilidad.ModoDisponibilidad.ODONTOLOGO;
import static ar.turnos.odontologia.disponibilidad.ModoDisponibilidad.PUBLICA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.turnos.odontologia.auth.JwtService;
import ar.turnos.odontologia.auth.Odontologo;
import ar.turnos.odontologia.auth.OdontologoRepository;
import ar.turnos.odontologia.auth.OdontologoUserDetailsService;
import ar.turnos.odontologia.common.ApiErrorFactory;
import ar.turnos.odontologia.common.ApiException;
import ar.turnos.odontologia.common.GlobalExceptionHandler;
import ar.turnos.odontologia.config.AppPropertiesConfig;
import ar.turnos.odontologia.config.ClockConfig;
import ar.turnos.odontologia.config.SecurityConfig;
import ar.turnos.odontologia.security.ApiAuthenticationEntryPoint;

/** Tests de los dos endpoints con la seguridad real del proyecto (sin base de datos). */
@WebMvcTest({ DisponibilidadPublicaController.class, DisponibilidadController.class })
@ActiveProfiles("test")
@Import({ SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiErrorFactory.class,
        GlobalExceptionHandler.class, ClockConfig.class, AppPropertiesConfig.class,
        JwtService.class, OdontologoUserDetailsService.class })
class DisponibilidadControllerTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 12, 1);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwt;

    @MockitoBean
    private DisponibilidadService service;

    @MockitoBean
    private OdontologoRepository odontologos;

    @Test
    void publicaSinTokenDevuelve200ConLaFormaDelContratoYModoPublico() throws Exception {
        when(service.consultar(any(), any(), any(), eq(PUBLICA))).thenReturn(respuesta());

        String cuerpo = mvc.perform(get("/api/public/disponibilidad")
                .param("prestacionId", "1")
                .param("desde", "2026-12-01")
                .param("hasta", "2026-12-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prestacion.id").value(1))
                .andExpect(jsonPath("$.prestacion.nombre").value("Consulta"))
                .andExpect(jsonPath("$.prestacion.duracionMinutos").value(30))
                .andExpect(jsonPath("$.dias[0].fecha").value("2026-12-01"))
                .andExpect(jsonPath("$.dias[0].horarios[0]").value("2026-12-01T09:00:00-03:00"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("paciente", "token");
        ArgumentCaptor<ModoDisponibilidad> modo = ArgumentCaptor.forClass(ModoDisponibilidad.class);
        verify(service).consultar(eq(1L), eq(LUNES), eq(LUNES), modo.capture());
        assertThat(modo.getValue()).isEqualTo(PUBLICA);
    }

    @Test
    void privadaSinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/disponibilidad")
                .param("prestacionId", "1")
                .param("desde", "2026-12-01")
                .param("hasta", "2026-12-01"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void privadaConTokenDevuelve200YModoOdontologo() throws Exception {
        when(odontologos.findByEmail("odontologo@ejemplo.com"))
                .thenReturn(Optional.of(new Odontologo("Odontólogo Ejemplo", "odontologo@ejemplo.com",
                        "hash-bcrypt-ficticio")));
        when(service.consultar(any(), any(), any(), eq(ODONTOLOGO))).thenReturn(respuesta());
        String token = jwt.emitir("odontologo@ejemplo.com").token();

        mvc.perform(get("/api/disponibilidad")
                .header("Authorization", "Bearer " + token)
                .param("prestacionId", "1")
                .param("desde", "2026-12-01")
                .param("hasta", "2026-12-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias[0].horarios[0]").value("2026-12-01T09:00:00-03:00"));

        ArgumentCaptor<ModoDisponibilidad> modo = ArgumentCaptor.forClass(ModoDisponibilidad.class);
        verify(service).consultar(eq(1L), eq(LUNES), eq(LUNES), modo.capture());
        assertThat(modo.getValue()).isEqualTo(ODONTOLOGO);
    }

    @Test
    void erroresDelServiceUsanElFormatoUniforme() throws Exception {
        when(service.consultar(any(), any(), any(), eq(PUBLICA)))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", "Hay campos con errores."))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO",
                        "No encontramos el recurso solicitado."))
                .thenThrow(new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRESTACION_INACTIVA",
                        "La prestación no está disponible para reservar."));

        mvc.perform(get("/api/public/disponibilidad")
                .param("prestacionId", "1").param("desde", "2026-12-01").param("hasta", "2026-11-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.path").value("/api/public/disponibilidad"));

        mvc.perform(get("/api/public/disponibilidad")
                .param("prestacionId", "1").param("desde", "2026-12-01").param("hasta", "2026-12-01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));

        mvc.perform(get("/api/public/disponibilidad")
                .param("prestacionId", "1").param("desde", "2026-12-01").param("hasta", "2026-12-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PRESTACION_INACTIVA"));
    }

    @Test
    void fechaConFormatoInvalidoDa400SolicitudMalFormada() throws Exception {
        mvc.perform(get("/api/public/disponibilidad")
                .param("prestacionId", "1").param("desde", "01-12-2026").param("hasta", "2026-12-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_MAL_FORMADA"));
    }

    private static DisponibilidadResponse respuesta() {
        return new DisponibilidadResponse(
                new PrestacionRefResponse(1L, "Consulta", 30),
                List.of(new DiaDisponibleResponse(LUNES, List.of(
                        java.time.OffsetDateTime.of(2026, 12, 1, 9, 0, 0, 0, ZoneOffset.ofHours(-3))))));
    }
}
