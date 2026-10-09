package com.nexus.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

/** Prueba el access-denied handler 403 de nexus-AUTH-02 (R4) sobre {@code MockHttpServletResponse}. */
class RestAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final RestAccessDeniedHandler handler = new RestAccessDeniedHandler(objectMapper);

    @Test
    @DisplayName("should return 403 with error envelope when access is denied")
    void shouldReturn403WithErrorEnvelopeWhenAccessIsDenied() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/events/user/8");
        request.setRequestURI("/api/events/user/8");
        var response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.hasNonNull("status")).isTrue();
        assertThat(json.hasNonNull("error")).isTrue();
        assertThat(json.hasNonNull("message")).isTrue();
        assertThat(json.hasNonNull("timestamp")).isTrue();
        assertThat(json.get("path").asText()).isEqualTo("/api/events/user/8");
    }
}
