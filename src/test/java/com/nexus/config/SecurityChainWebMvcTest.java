package com.nexus.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.controller.EventController;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.JwtService;
import com.nexus.service.EventService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Prueba la cadena de filtros <b>real</b> (CORS, filtro JWT, entry point, autorizacion) con una
 * rebanada {@code @WebMvcTest} + {@code @Import(SecurityConfig.class)}, sin MySQL (nexus-AUTH-02,
 * T-16b). Cubre la frontera que antes solo se veia en el navegador: el preflight.
 */
@WebMvcTest(EventController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:8081")
class SecurityChainWebMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private EventService eventService;

    @MockBean private JwtService jwtService;

    @Test
    @DisplayName("should return 401 with envelope when protected route has no token")
    void shouldReturn401WithEnvelopeWhenProtectedRouteHasNoToken() throws Exception {
        mockMvc.perform(get("/events/user/7"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/events/user/7"));

        verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("should return 401 when token does not verify")
    void shouldReturn401WhenTokenDoesNotVerify() throws Exception {
        mockMvc.perform(get("/events/user/7").header("Authorization", "Bearer basura"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should reach controller when token is valid")
    void shouldReachControllerWhenTokenIsValid() throws Exception {
        given(jwtService.verify("tok")).willReturn(Optional.of(new AuthenticatedUser(7L, "a@b.c")));
        given(eventService.getUserEvents(7L)).willReturn(List.of());

        mockMvc.perform(get("/events/user/7").header("Authorization", "Bearer tok"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should serve health without token when route is public")
    void shouldServeHealthWithoutTokenWhenRouteIsPublic() throws Exception {
        mockMvc.perform(get("/events/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("should answer preflight without authentication when origin is allowed")
    void shouldAnswerPreflightWithoutAuthenticationWhenOriginIsAllowed() throws Exception {
        mockMvc.perform(
                        options("/events/user/7")
                                .header("Origin", "http://localhost:8081")
                                .header("Access-Control-Request-Method", "GET")
                                .header("Authorization", "Bearer basura"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));

        then(jwtService).shouldHaveNoInteractions();
    }
}
