package ar.turnos.odontologia.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import ar.turnos.odontologia.auth.JwtService;
import ar.turnos.odontologia.auth.Odontologo;
import ar.turnos.odontologia.auth.OdontologoPrincipal;
import ar.turnos.odontologia.config.AppProperties;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class JwtAuthFilterTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String SECRETO = "secreto-de-prueba-ficticio-de-al-menos-32-bytes-0123456789";
    private static final Instant AHORA = Instant.parse("2026-12-01T23:00:00Z");

    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tokenValidoAutenticaAlOdontologoYSigueLaCadena() throws Exception {
        Odontologo odontologo = new Odontologo("Odontólogo Ejemplo", "odontologo@ejemplo.com", "hash-bcrypt-ficticio");
        ReflectionTestUtils.setField(odontologo, "id", 7L);
        OdontologoPrincipal principal = new OdontologoPrincipal(odontologo);
        when(userDetailsService.loadUserByUsername("odontologo@ejemplo.com")).thenReturn(principal);
        String token = jwt(Clock.fixed(AHORA, ZoneOffset.UTC)).emitir("  Odontologo@Ejemplo.com  ").token();

        MockHttpServletRequest request = requestConBearer(token);
        MockFilterChain cadena = new MockFilterChain();
        filtro(jwt(Clock.fixed(AHORA, ZoneOffset.UTC))).doFilter(request, new MockHttpServletResponse(), cadena);

        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        assertThat(autenticacion).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(autenticacion.getPrincipal()).isSameAs(principal);
        assertThat(autenticacion.getAuthorities()).isEmpty();
        assertThat(cadena.getRequest()).isSameAs(request);
        verify(userDetailsService).loadUserByUsername("odontologo@ejemplo.com");
    }

    @Test
    void tokenVencidoNoAutenticaYNologueaElToken() throws Exception {
        String token = jwt(Clock.fixed(AHORA, ZoneOffset.UTC)).emitir("odontologo@ejemplo.com").token();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(JwtAuthFilter.class);
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = requestConBearer(token);
            MockFilterChain cadena = new MockFilterChain();

            filtro(jwt(Clock.fixed(AHORA.plusSeconds(3601), ZoneOffset.UTC)))
                    .doFilter(request, new MockHttpServletResponse(), cadena);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(cadena.getRequest()).isSameAs(request);
            assertThat(appender.list).noneMatch(evento -> evento.getFormattedMessage().contains(token)
                    || evento.getFormattedMessage().contains("Authorization")
                    || evento.getFormattedMessage().contains(SECRETO));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void sinHeaderBearerONoPortadorNoAutentica() throws Exception {
        MockHttpServletRequest sinHeader = new MockHttpServletRequest("GET", "/api/auth/me");
        MockFilterChain cadenaSinHeader = new MockFilterChain();
        filtro(jwt(Clock.fixed(AHORA, ZoneOffset.UTC)))
                .doFilter(sinHeader, new MockHttpServletResponse(), cadenaSinHeader);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(cadenaSinHeader.getRequest()).isSameAs(sinHeader);

        MockHttpServletRequest conBasic = new MockHttpServletRequest("GET", "/api/auth/me");
        conBasic.addHeader("Authorization", "Basic " + SECRETO);
        MockFilterChain cadenaConBasic = new MockFilterChain();
        filtro(jwt(Clock.fixed(AHORA, ZoneOffset.UTC)))
                .doFilter(conBasic, new MockHttpServletResponse(), cadenaConBasic);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(cadenaConBasic.getRequest()).isSameAs(conBasic);
    }

    @Test
    void subjectInexistenteNoAutentica() throws Exception {
        when(userDetailsService.loadUserByUsername("inexistente@ejemplo.com"))
                .thenThrow(new UsernameNotFoundException("No se encontró el odontólogo."));
        String token = jwt(Clock.fixed(AHORA, ZoneOffset.UTC)).emitir("inexistente@ejemplo.com").token();

        filtro(jwt(Clock.fixed(AHORA, ZoneOffset.UTC)))
                .doFilter(requestConBearer(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private JwtAuthFilter filtro(JwtService jwt) {
        return new JwtAuthFilter(jwt, userDetailsService);
    }

    private MockHttpServletRequest requestConBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private JwtService jwt(Clock clock) {
        AppProperties app = new AppProperties(ZONA, "http://localhost:8080", null, null, null, null, null, null,
                null, new AppProperties.Jwt(SECRETO, 60), null);
        return new JwtService(app, clock);
    }
}
