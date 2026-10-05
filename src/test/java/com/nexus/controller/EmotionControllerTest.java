package com.nexus.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.config.SecurityConfig;
import com.nexus.dto.EmotionLogResponse;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.JwtService;
import com.nexus.service.EmotionService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guardas de nexus-SEC-04/RN-28 en {@link EmotionController}: registro e historial son privados
 * -- ni siquiera la pareja vinculada puede leerlos (a diferencia del avatar de perfil). Por eso
 * usa {@code OwnershipGuard.requireSelf}, no {@code LinkMembershipGuard}.
 */
@WebMvcTest(EmotionController.class)
@Import(SecurityConfig.class)
class EmotionControllerTest {

    private static final long USER_ID = 4L;

    private static final long OTHER_USER_ID = 9L;

    private static final String USER_EMAIL = "a@b.co";

    private static final String BEARER = "Bearer test-token";

    @Autowired private MockMvc mockMvc;

    @MockBean private EmotionService emotionService;

    @MockBean private JwtService jwtService;

    private void authenticateAs(long userId) {
        given(jwtService.parse(anyString())).willReturn(new AuthenticatedUser(userId, USER_EMAIL));
    }

    @Test
    @DisplayName("should return 201 when logging your own emotion")
    void shouldReturn201WhenLoggingYourOwnEmotion() throws Exception {
        authenticateAs(USER_ID);
        given(emotionService.logEmotion(eq(USER_ID), any()))
                .willReturn(
                        EmotionLogResponse.builder()
                                .id(1L)
                                .valence(0.5)
                                .activation(0.2)
                                .loggedAt(Instant.parse("2026-10-06T12:00:00Z"))
                                .build());

        mockMvc.perform(
                        post("/emotions/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"valence\":0.5,\"activation\":0.2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.valence").value(0.5));
    }

    @Test
    @DisplayName("should return 400 when valence is out of range")
    void shouldReturn400WhenValenceIsOutOfRange() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(
                        post("/emotions/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"valence\":5.0,\"activation\":0.2}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(emotionService);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when logging an emotion as another user")
    void shouldReturn403AndNotTouchTheServiceWhenLoggingAnEmotionAsAnotherUser() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        post("/emotions/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"valence\":0.5,\"activation\":0.2}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(emotionService);
    }

    @Test
    @DisplayName("should return 200 when reading your own history")
    void shouldReturn200WhenReadingYourOwnHistory() throws Exception {
        authenticateAs(USER_ID);
        given(emotionService.getHistory(USER_ID)).willReturn(List.of());

        mockMvc.perform(get("/emotions/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when reading another user's history, even the linked partner")
    void shouldReturn403AndNotTouchTheServiceWhenReadingAnotherUsersHistoryEvenTheLinkedPartner()
            throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(get("/emotions/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(emotionService);
    }
}
