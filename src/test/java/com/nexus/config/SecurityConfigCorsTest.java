package com.nexus.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Politica CORS efectiva del bean {@code corsConfigurationSource}.
 *
 * <p>No levanta contexto de Spring ni toca MySQL: construye {@link SecurityConfig} a mano y
 * interroga la {@link CorsConfiguration} resultante. Cubre R1, R2 y R3 de nexus-AUTH-06.
 */
class SecurityConfigCorsTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:8081";
    private static final String FOREIGN_ORIGIN = "http://evil.example";

    private CorsConfiguration configuration;

    @BeforeEach
    void resolveConfigurationForAnApiPath() {
        CorsConfigurationSource source =
                new SecurityConfig()
                        .corsConfigurationSource(new CorsProperties(List.of(ALLOWED_ORIGIN)));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/events/1");
        request.setRequestURI("/events/1");

        configuration = source.getCorsConfiguration(request);
        assertThat(configuration).as("la config debe cubrir /**").isNotNull();
    }

    @Test
    @DisplayName("R1 - un origen ajeno no obtiene Access-Control-Allow-Origin")
    void shouldRejectOriginWhenOriginIsNotAllowed() {
        assertThat(configuration.checkOrigin(FOREIGN_ORIGIN)).isNull();
    }

    @Test
    @DisplayName("R2 - un origen permitido se devuelve exacto, nunca como comodin")
    void shouldEchoExactOriginWhenOriginIsAllowed() {
        assertThat(configuration.checkOrigin(ALLOWED_ORIGIN))
                .isEqualTo(ALLOWED_ORIGIN)
                .isNotEqualTo("*");
    }

    @Test
    @DisplayName("R3 - no se declaran credenciales: ningun cliente usa cookies")
    void shouldDisableCredentialsWhenCorsIsConfigured() {
        assertThat(configuration.getAllowCredentials()).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("R3 frontera - Authorization permitido, cabeceras arbitrarias no")
    void shouldAllowAuthorizationHeaderWhenPreflightRequestsIt() {
        assertThat(configuration.checkHeaders(List.of("authorization", "content-type")))
                .containsExactlyInAnyOrder("authorization", "content-type");
        assertThat(configuration.checkHeaders(List.of("x-forwarded-for"))).isNull();
    }
}
