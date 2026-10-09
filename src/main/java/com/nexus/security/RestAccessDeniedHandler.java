package com.nexus.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Responde 403 con el sobre {@link ErrorResponse} de la casa cuando una peticion <b>autenticada</b>
 * es rechazada por autorizacion (R4). No lleva {@code WWW-Authenticate}: ya hay identidad, el
 * problema es de permisos.
 *
 * <p>Clase plana (sin {@code @Component}), por el mismo motivo que {@link RestAuthenticationEntryPoint}.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse body =
                new ErrorResponse(
                        HttpStatus.FORBIDDEN.value(),
                        "Forbidden",
                        "No tienes permiso para acceder a este recurso",
                        request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
