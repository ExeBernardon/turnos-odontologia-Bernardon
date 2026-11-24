package ar.turnos.odontologia.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import ar.turnos.odontologia.common.ApiError;
import ar.turnos.odontologia.common.ApiErrorFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 401 NO_AUTENTICADO con el formato uniforme (docs/api/errores.md): lo emite Spring Security, no un controller. */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String MENSAJE = "Tenés que iniciar sesión para continuar.";

    private final ApiErrorFactory errores;
    private final ObjectMapper mapper;

    public ApiAuthenticationEntryPoint(ApiErrorFactory errores, ObjectMapper mapper) {
        this.errores = errores;
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        ApiError cuerpo = errores.crear(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", MENSAJE, request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), cuerpo);
    }
}
