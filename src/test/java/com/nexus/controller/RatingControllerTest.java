package com.nexus.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.config.SecurityConfig;
import com.nexus.dto.PendingRatingResponse;
import com.nexus.dto.RatingResponse;
import com.nexus.exception.BadRequestException;
import com.nexus.exception.ConflictException;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.JwtService;
import com.nexus.service.RatingService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** CU42 - {@link RatingController}: cada quien solo con su {@code {userId}}; 400/409 del servicio. */
@WebMvcTest(RatingController.class)
@Import(SecurityConfig.class)
class RatingControllerTest {

    private static final long USER_ID = 1L;
    private static final String BEARER = "Bearer test-token";
    private static final Instant END = Instant.parse("2026-10-19T02:00:00Z");

    @Autowired private MockMvc mockMvc;
    @MockBean private RatingService service;
    @MockBean private JwtService jwtService;

    @BeforeEach
    void authenticate() {
        given(jwtService.parse(anyString())).willReturn(new AuthenticatedUser(USER_ID, "a@b.co"));
    }

    @Test
    @DisplayName("sin token, 401")
    void requiresToken() throws Exception {
        mockMvc.perform(get("/ratings/" + USER_ID + "/pending")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("lista las citas por calificar de la propia persona")
    void listsPending() throws Exception {
        given(service.pending(USER_ID)).willReturn(List.of(new PendingRatingResponse(
                5L, "Cine al aire libre", END.minusSeconds(7200), END, "Coyoacán", END.plusSeconds(172_800))));

        mockMvc.perform(get("/ratings/" + USER_ID + "/pending").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(5))
                .andExpect(jsonPath("$[0].title").value("Cine al aire libre"))
                .andExpect(jsonPath("$[0].expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("califica con el propio usuario: 201 y sin el puntaje en la respuesta")
    void rates() throws Exception {
        given(service.rate(USER_ID, 5L, 4)).willReturn(new RatingResponse(5L, END));

        mockMvc.perform(post("/ratings/" + USER_ID + "/events/5").header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value(5))
                .andExpect(jsonPath("$.score").doesNotExist());
    }

    @Test
    @DisplayName("con el usuario de otra persona, 403 y no se toca nada")
    void otherUserIsForbidden() throws Exception {
        mockMvc.perform(get("/ratings/2/pending").header("Authorization", BEARER))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("un puntaje fuera de 1 a 5 se rechaza antes del servicio")
    void scoreOutOfRange() throws Exception {
        mockMvc.perform(post("/ratings/" + USER_ID + "/events/5").header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\":7}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("FA01 vencida 400; FA02 ya calificada 409")
    void serviceErrors() throws Exception {
        given(service.rate(eq(USER_ID), eq(5L), anyInt())).willThrow(new BadRequestException("Ya no es posible calificar esta cita."));
        given(service.rate(eq(USER_ID), eq(6L), anyInt())).willThrow(new ConflictException("Ya calificaste esta cita."));

        mockMvc.perform(post("/ratings/" + USER_ID + "/events/5").header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\":3}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/ratings/" + USER_ID + "/events/6").header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\":3}"))
                .andExpect(status().isConflict());
    }
}
