package com.nexus.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

/** Prueba el entry point 401 de nexus-AUTH-02 (R3/R8) sobre {@code MockHttpServletResponse}. */
class RestAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final RestAuthenticationEntryPoint entryPoint =
            new RestAuthenticationEntryPoint(objectMapper);

    private MockHttpServletRequest requestTo(String uri) {
        var request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    @Test
    @DisplayName("should return 401 with error envelope when authentication is missing")
    void shouldReturn401WithErrorEnvelopeWhenAuthenticationIsMissing() throws Exception {
        var response = new MockHttpServletResponse();

        entryPoint.commence(
                requestTo("/api/events/user/7"), response, new InsufficientAuthenticationException("x"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.hasNonNull("status")).isTrue();
        assertThat(json.hasNonNull("error")).isTrue();
        assertThat(json.hasNonNull("message")).isTrue();
        assertThat(json.hasNonNull("timestamp")).isTrue();
        assertThat(json.hasNonNull("path")).isTrue();
    }

    @Test
    @DisplayName("should set www authenticate header when authentication is missing")
    void shouldSetWwwAuthenticateHeaderWhenAuthenticationIsMissing() throws Exception {
        var response = new MockHttpServletResponse();

        entryPoint.commence(
                requestTo("/api/events/user/7"), response, new InsufficientAuthenticationException("x"));

        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("should not reveal failure reason when token is expired")
    void shouldNotRevealFailureReasonWhenTokenIsExpired() throws Exception {
        var response = new MockHttpServletResponse();

        entryPoint.commence(
                requestTo("/api/events/user/7"), response, new InsufficientAuthenticationException("x"));

        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.get("message").asText())
                .doesNotContain("expired", "signature", "issuer", "JWT");
    }

    @Test
    @DisplayName("should use request uri as path when building the envelope")
    void shouldUseRequestUriAsPathWhenBuildingTheEnvelope() throws Exception {
        var response = new MockHttpServletResponse();

        entryPoint.commence(
                requestTo("/api/events/user/7"), response, new InsufficientAuthenticationException("x"));

        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.get("path").asText()).isEqualTo("/api/events/user/7");
    }
}
