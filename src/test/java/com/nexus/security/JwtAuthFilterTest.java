package com.nexus.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Pruebas de {@link JwtAuthFilter} en aislamiento: {@link JwtService} va mockeado, la validacion
 * real del token la cubre {@code JwtServiceTest}. Aqui se prueba el cableado: que header produce
 * que estado del {@code SecurityContext}, y que la cadena de filtros siempre continua.
 */
class JwtAuthFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtService);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should authenticate when the header carries a valid bearer token")
    void shouldAuthenticateWhenTheHeaderCarriesAValidBearerToken() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(7L, "a@b.co");
        given(request.getHeader("Authorization")).willReturn("Bearer t.o.k");
        given(jwtService.parse("t.o.k")).willReturn(user);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(user);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("should not authenticate when the header is missing")
    void shouldNotAuthenticateWhenTheHeaderIsMissing() throws Exception {
        given(request.getHeader("Authorization")).willReturn(null);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("should not authenticate when the header does not start with Bearer")
    void shouldNotAuthenticateWhenTheHeaderDoesNotStartWithBearer() throws Exception {
        given(request.getHeader("Authorization")).willReturn("Basic xyz");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("should not authenticate but still continue the chain when the token is invalid")
    void shouldNotAuthenticateButStillContinueTheChainWhenTheTokenIsInvalid() throws Exception {
        given(request.getHeader("Authorization")).willReturn("Bearer expirado");
        given(jwtService.parse("expirado")).willThrow(new ExpiredJwtException(null, null, "caduco"));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
