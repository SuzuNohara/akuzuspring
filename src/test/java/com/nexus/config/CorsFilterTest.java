package com.nexus.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import jakarta.servlet.ServletException;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Politica CORS ejercitada a traves del filtro real, no solo de la {@code CorsConfiguration}.
 *
 * <p>Cubre R6 de nexus-AUTH-06: un cliente nativo (Expo Go, APK) no envia cabecera {@code Origin},
 * su peticion no es CORS y el filtro debe dejarla pasar intacta hasta la aplicacion. El plan
 * sostenia que R6 «no es automatizable sin contexto completo, que arrastra MySQL»; no es cierto:
 * {@link CorsFilter} se instrumenta con un {@link MockFilterChain} sin contexto de Spring y sin base
 * de datos.
 *
 * <p>La diferencia con {@code SecurityConfigCorsTest} es lo que se mide: alli, la configuracion
 * declarada; aqui, el codigo de estado y las cabeceras que el filtro escribe de verdad.
 *
 * @implNote O(1) por caso: una peticion simulada, sin E/S ni red.
 */
class CorsFilterTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:8081";
    private static final String FOREIGN_ORIGIN = "http://evil.example";
    private static final String ORIGIN = "Origin";
    private static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    private static final String REQUEST_METHOD = "Access-Control-Request-Method";
    private static final String API_PATH = "/events/1";

    private static final int OK = 200;
    private static final int FORBIDDEN = 403;

    private CorsFilter filter;
    private MockFilterChain chain;
    private MockHttpServletResponse response;

    @BeforeEach
    void buildFilterOverTheRealPolicy() {
        // jwtService no participa en corsConfigurationSource(); null es seguro aqui.
        CorsConfigurationSource source =
                new SecurityConfig(null)
                        .corsConfigurationSource(new CorsProperties(List.of(ALLOWED_ORIGIN)));
        filter = new CorsFilter(source);
        chain = new MockFilterChain();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("R2 - un preflight desde origen permitido recibe el origen exacto")
    void shouldEchoExactOriginWhenPreflightComesFromAllowedOrigin()
            throws ServletException, IOException {
        MockHttpServletRequest request = preflightFrom(ALLOWED_ORIGIN);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(OK);
        assertThat(response.getHeader(ALLOW_ORIGIN)).isEqualTo(ALLOWED_ORIGIN).isNotEqualTo("*");
        assertThat(response.getHeader(ALLOW_CREDENTIALS))
                .as("ningun cliente usa cookies: R3")
                .isNull();
    }

    @Test
    @DisplayName("R1 - un preflight desde origen ajeno se rechaza con 403")
    void shouldRejectPreflightWhenOriginIsForeign() throws ServletException, IOException {
        MockHttpServletRequest request = preflightFrom(FOREIGN_ORIGIN);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(FORBIDDEN);
        assertThat(response.getHeader(ALLOW_ORIGIN)).isNull();
        assertThat(chain.getRequest())
                .as("un preflight rechazado no llega a la aplicacion")
                .isNull();
    }

    @Test
    @DisplayName("R1 - una peticion simple desde origen ajeno se rechaza con 403")
    void shouldRejectSimpleRequestWhenOriginIsForeign() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", API_PATH);
        request.addHeader(ORIGIN, FOREIGN_ORIGIN);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(FORBIDDEN);
        assertThat(response.getHeader(ALLOW_ORIGIN)).isNull();
        assertThat(chain.getRequest())
                .as("una peticion de origen ajeno no llega a la aplicacion")
                .isNull();
    }

    @Test
    @DisplayName("R6 - una peticion sin Origin llega intacta a la aplicacion")
    void shouldPassRequestThroughWhenThereIsNoOriginHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", API_PATH);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(OK);
        assertThat(response.getHeader(ALLOW_ORIGIN))
                .as("no es una peticion CORS: el filtro no anade cabeceras")
                .isNull();
        assertThat(chain.getRequest())
                .as("el cliente nativo llega a la aplicacion: R6")
                .isSameAs(request);
    }

    private static MockHttpServletRequest preflightFrom(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", API_PATH);
        request.addHeader(ORIGIN, origin);
        request.addHeader(REQUEST_METHOD, "GET");
        return request;
    }
}
