package com.nexus.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valida el JWT de la cabecera {@code Authorization} y, si es valido, instala un
 * {@link AuthenticatedUser} en el {@code SecurityContext} para el resto de la cadena y los
 * controllers (nexus-SEC-04).
 *
 * <p>No es un {@code @Component}: {@link com.nexus.config.SecurityConfig} lo construye a mano con
 * el {@link JwtService} que ya recibe por constructor, para que los slices {@code @WebMvcTest}
 * existentes (que ya traen {@code @MockBean JwtService}) no necesiten un import adicional.
 *
 * <p>Si el token falta, esta malformado, caduco o no verifica, el filtro no autentica y deja que
 * {@code authorizeHttpRequests()} de {@code SecurityConfig} decida la respuesta -- nunca lanza ni
 * corta la cadena por su cuenta.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                AuthenticatedUser user = jwtService.parse(token);
                SecurityContextHolder.getContext()
                        .setAuthentication(
                                new UsernamePasswordAuthenticationToken(user, null, List.of()));
            } catch (JwtException | IllegalArgumentException invalidToken) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
