package com.nexus.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

/**
 * Afirma la lista de rutas publicas (R5) sin levantar contexto: solo {@code AntPathMatcher} sobre
 * {@link PublicRoutes#PATTERNS}. Prueba la lista, no el cableado.
 */
class PublicRoutesTest {

    private final AntPathMatcher matcher = new AntPathMatcher();

    private boolean isPublic(String path) {
        return Arrays.stream(PublicRoutes.PATTERNS).anyMatch(p -> matcher.match(p, path));
    }

    @Test
    @DisplayName("should match auth routes when checking public patterns")
    void shouldMatchAuthRoutesWhenCheckingPublicPatterns() {
        assertThat(isPublic("/auth/login")).isTrue();
        assertThat(isPublic("/auth/register")).isTrue();
        assertThat(isPublic("/auth/health")).isTrue();
    }

    @Test
    @DisplayName("should match the three health endpoints when checking public patterns")
    void shouldMatchTheThreeHealthEndpointsWhenCheckingPublicPatterns() {
        assertThat(isPublic("/auth/health")).isTrue();
        assertThat(isPublic("/events/health")).isTrue();
        assertThat(isPublic("/calendars/external/health")).isTrue();
    }

    @Test
    @DisplayName("should not match business routes when checking public patterns")
    void shouldNotMatchBusinessRoutesWhenCheckingPublicPatterns() {
        assertThat(isPublic("/events/user/7")).isFalse();
        assertThat(isPublic("/profile/7/avatar")).isFalse();
        assertThat(isPublic("/link/status/7")).isFalse();
        assertThat(isPublic("/preferences/user/7")).isFalse();
        assertThat(isPublic("/availability/schedule/7")).isFalse();
        assertThat(isPublic("/calendars/external/7")).isFalse();
    }

    @Test
    @DisplayName("should match error dispatch when checking public patterns")
    void shouldMatchErrorDispatchWhenCheckingPublicPatterns() {
        assertThat(isPublic("/error")).isTrue();
    }
}
