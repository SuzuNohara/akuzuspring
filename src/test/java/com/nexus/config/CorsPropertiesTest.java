package com.nexus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guarda de configuracion: la lista de origenes CORS nunca puede contener un comodin.
 *
 * <p>Cubre R4 de nexus-AUTH-06: reintroducir {@code *} debe romper el arranque, no
 * abrir CORS en silencio.
 */
class CorsPropertiesTest {

    @Test
    @DisplayName("R4 - un origen con comodin revienta la construccion de las propiedades")
    void shouldRejectOriginListWhenEntryContainsWildcard() {
        assertThatThrownBy(() -> new CorsProperties(List.of("*")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.cors.allowed-origins");

        assertThatThrownBy(() -> new CorsProperties(List.of("http://localhost:*")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.cors.allowed-origins");

        assertThatThrownBy(
                        () ->
                                new CorsProperties(
                                        List.of("http://localhost:8081", "https://*.example.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.cors.allowed-origins");
    }

    @Test
    @DisplayName("R4 - ausencia de configuracion es fallo seguro: lista vacia, sin excepcion")
    void shouldAcceptEmptyListWhenOriginsAreNotConfigured() {
        assertThat(new CorsProperties(null).allowedOrigins()).isEmpty();
        assertThat(new CorsProperties(List.of()).allowedOrigins()).isEmpty();
    }

    @Test
    @DisplayName("R4 - una lista de origenes exactos se conserva tal cual")
    void shouldKeepOriginsWhenAllEntriesAreExact() {
        assertThat(new CorsProperties(List.of("http://localhost:8081")).allowedOrigins())
                .containsExactly("http://localhost:8081");
    }
}
