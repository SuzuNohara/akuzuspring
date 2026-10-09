package com.nexus.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Responde 401 con el sobre {@link ErrorResponse} de la casa cuando una peticion sin autenticacion
 * valida alcanza una ruta protegida (R3). Es el unico sitio que escribe el 401: el filtro nunca lo
 * hace.
 *
 * <p>Clase plana (sin {@code @Component}): la instancia {@code SecurityConfig} con el
 * {@code ObjectMapper} del contexto. Como {@code @Component}, los {@code @WebMvcTest} que importan
 * {@code SecurityConfig} dejarian de arrancar.
 *
 * <p>El mensaje nunca dice por que fallo —ni caducidad, ni firma, ni emisor— para no dar un oraculo
 * a quien pruebe tokens (R8).
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");

        ErrorResponse body =
                new ErrorResponse(
                        HttpStatus.UNAUTHORIZED.value(),
                        "Unauthorized",
                        "Autenticacion requerida",
                        request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
