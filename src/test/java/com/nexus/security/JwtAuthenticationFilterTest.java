package com.nexus.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Prueba {@link JwtAuthenticationFilter} sin levantar contexto Spring: {@code MockHttpServletRequest},
 * {@code MockHttpServletResponse} y {@code MockFilterChain} son POJOs. Se emite un token de verdad y
 * se verifica de verdad (R1/R2/R7/R9 de nexus-AUTH-02).
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String OTHER_SECRET =
            "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz0";

    private final JwtService service =
            new JwtService(new JwtProperties(SECRET, 86_400_000L, "nexus-api"));
    private final JwtService forger =
            new JwtService(new JwtProperties(OTHER_SECRET, 86_400_000L, "nexus-api"));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(service);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should populate security context when bearer token is valid")
    void shouldPopulateSecurityContextWhenBearerTokenIsValid() throws Exception {
        var request = new MockHttpServletRequest("GET", "/events/user/7");
        request.addHeader("Authorization", "Bearer " + service.issueFor(7L, "a@b.c").token());
        var chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedUser(7L, "a@b.c"));
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("should leave context empty when authorization header is absent")
    void shouldLeaveContextEmptyWhenAuthorizationHeaderIsAbsent() throws Exception {
        var request = new MockHttpServletRequest("GET", "/events/user/7");
        var chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("should leave context empty when scheme is not bearer")
    void shouldLeaveContextEmptyWhenSchemeIsNotBearer() throws Exception {
        var request = new MockHttpServletRequest("GET", "/events/user/7");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        var chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("should leave context empty when token is forged")
    void shouldLeaveContextEmptyWhenTokenIsForged() throws Exception {
        var request = new MockHttpServletRequest("GET", "/events/user/7");
        request.addHeader("Authorization", "Bearer " + forger.issueFor(7L, "a@b.c").token());
        var chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("should not write to response when token is invalid")
    void shouldNotWriteToResponseWhenTokenIsInvalid() throws Exception {
        var request = new MockHttpServletRequest("GET", "/events/user/7");
        request.addHeader("Authorization", "Bearer no.es.un.jwt");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("should be case insensitive on header name when token is valid")
    void shouldBeCaseInsensitiveOnHeaderNameWhenTokenIsValid() throws Exception {
        var request = new MockHttpServletRequest("GET", "/events/user/7");
        request.addHeader("authorization", "Bearer " + service.issueFor(7L, "a@b.c").token());
        var chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("should skip filter when request is cors preflight")
    void shouldSkipFilterWhenRequestIsCorsPreflight() throws Exception {
        var request = new MockHttpServletRequest("OPTIONS", "/events/user/7");
        request.addHeader("Origin", "http://localhost:8081");
        request.addHeader("Access-Control-Request-Method", "GET");
        request.addHeader("Authorization", "Bearer basura");
        var chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }
}
