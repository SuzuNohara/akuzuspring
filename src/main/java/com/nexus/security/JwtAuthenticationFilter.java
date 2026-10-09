package com.nexus.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lee el {@code Authorization: Bearer <jwt>} de cada peticion y, si verifica, deja el principal en
 * el {@link SecurityContextHolder} antes de que se evalue la autorizacion de la ruta.
 *
 * <p>Nunca escribe en la respuesta: un token ausente o invalido produce «anonimo» y deja seguir la
 * cadena. El 401/403 lo emite el entry point / access-denied handler, en un solo sitio. No declara
 * logger a proposito (R8): asi no puede filtrar el token a ningun log.
 *
 * <p>No lleva {@code @Component} (invariante 2 de nexus-AUTH-06): se instancia en
 * {@code SecurityConfig}. Como {@code @Component}, Spring Boot lo registraria tambien a nivel de
 * contenedor de servlets, por delante del {@code CorsFilter}, y veria el preflight.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = HttpHeaders.AUTHORIZATION;
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /** No toca los preflight CORS (R7): los resuelve el {@code CorsFilter}, que va antes. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return CorsUtils.isPreFlightRequest(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            jwtService
                    .verify(header.substring(PREFIX.length()))
                    .ifPresent(user -> authenticate(user, request));
        }
        chain.doFilter(request, response);
    }

    private void authenticate(AuthenticatedUser user, HttpServletRequest request) {
        var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
