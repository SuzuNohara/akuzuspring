package com.nexus.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.config.SecurityConfig;
import com.nexus.entity.User;
import com.nexus.exception.BadRequestException;
import com.nexus.security.IssuedToken;
import com.nexus.security.JwtService;
import com.nexus.service.PreferenceService;
import com.nexus.service.UserService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de la superficie HTTP de {@link AuthController} en lo que toca a la emision de token
 * (nexus-AUTH-01).
 *
 * <p>{@link JwtService} va mockeado: aqui se prueba el cableado del controlador, no la firma. La
 * firma la cubre {@code JwtServiceTest}.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String LOGIN_PATH = "/auth/login";

    private static final String LOGIN_BODY =
            "{\"email\":\"a@b.co\",\"password\":\"Secreto123!\"}";

    private static final long USER_ID = 7L;

    private static final String USER_EMAIL = "a@b.co";

    @Autowired private MockMvc mockMvc;

    @MockBean private UserService userService;

    @MockBean private PreferenceService preferenceService;

    @MockBean private JwtService jwtService;

    @Test
    @DisplayName("should populate token and token expires at when login succeeds")
    void shouldPopulateTokenAndTokenExpiresAtWhenLoginSucceeds() throws Exception {
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        given(userService.login(anyString(), anyString())).willReturn(activeUser());
        given(preferenceService.hasCompletedQuestionnaire(USER_ID)).willReturn(true);
        given(jwtService.issueFor(USER_ID, USER_EMAIL))
                .willReturn(new IssuedToken("t.o.k", expiresAt));

        mockMvc.perform(
                        post(LOGIN_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("t.o.k"))
                .andExpect(jsonPath("$.tokenExpiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("should serialize token expires at as utc iso 8601 when login succeeds")
    void shouldSerializeTokenExpiresAtAsUtcIso8601WhenLoginSucceeds() throws Exception {
        Instant expiresAt = Instant.parse("2026-09-08T21:14:03Z");
        given(userService.login(anyString(), anyString())).willReturn(activeUser());
        given(preferenceService.hasCompletedQuestionnaire(USER_ID)).willReturn(true);
        given(jwtService.issueFor(anyLong(), anyString()))
                .willReturn(new IssuedToken("t.o.k", expiresAt));

        String body =
                mockMvc.perform(
                                post(LOGIN_PATH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(LOGIN_BODY))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(body).containsPattern("\"tokenExpiresAt\":\"\\d{4}-\\d{2}-\\d{2}T[^\"]*Z\"");
        assertThat(body).doesNotContainPattern("\"tokenExpiresAt\":\\d");
    }

    @Test
    @DisplayName("should return null token when account is not verified")
    void shouldReturnNullTokenWhenAccountIsNotVerified() throws Exception {
        given(userService.login(anyString(), anyString()))
                .willThrow(new BadRequestException("ACCOUNT_NOT_VERIFIED"));

        mockMvc.perform(
                        post(LOGIN_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(LOGIN_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.tokenExpiresAt").doesNotExist());

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("should not issue token when credentials are invalid")
    void shouldNotIssueTokenWhenCredentialsAreInvalid() throws Exception {
        given(userService.login(anyString(), anyString()))
                .willThrow(new BadRequestException("Credenciales incorrectas"));

        mockMvc.perform(
                        post(LOGIN_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(LOGIN_BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("should not issue token when account is locked")
    void shouldNotIssueTokenWhenAccountIsLocked() throws Exception {
        given(userService.login(anyString(), anyString()))
                .willThrow(
                        new BadRequestException(
                                "Tu cuenta esta bloqueada temporalmente por seguridad."));

        mockMvc.perform(
                        post(LOGIN_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(LOGIN_BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jwtService);
    }

    private static User activeUser() {
        return User.builder()
                .id(USER_ID)
                .email(USER_EMAIL)
                .emailConfirmed(true)
                .displayName("Ada")
                .nickname("ada")
                .linkCode("ABCD-1234")
                .build();
    }
}
