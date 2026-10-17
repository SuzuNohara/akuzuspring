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
import com.nexus.dto.planning.PlanningMember;
import com.nexus.dto.planning.PlanningView;
import com.nexus.exception.ConflictException;
import com.nexus.model.Vote;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.JwtService;
import com.nexus.service.planning.PlanningService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** {@link PlanningController}: cada quien solo con su {@code {userId}}; 204 sin planeacion; 409. */
@WebMvcTest(PlanningController.class)
@Import(SecurityConfig.class)
class PlanningControllerTest {

    private static final long USER_ID = 4L;
    private static final String BEARER = "Bearer test-token";

    @Autowired private MockMvc mockMvc;
    @MockBean private PlanningService service;
    @MockBean private JwtService jwtService;

    @BeforeEach
    void authenticate() {
        given(jwtService.parse(anyString())).willReturn(new AuthenticatedUser(USER_ID, "a@b.co"));
    }

    private static PlanningView view(String phase) {
        return new PlanningView(50L, "ACTIVE", phase, "INVITER", new PlanningMember(USER_ID, "Ana", true, false),
                new PlanningMember(13L, "Sam", false, false), Instant.parse("2026-10-18T18:00:00Z"), null, null,
                List.of(), List.of(), false, Set.of(), false, null, null, null, 0);
    }

    @Test
    @DisplayName("sin planeacion, 204")
    void noCurrent() throws Exception {
        given(service.current(USER_ID)).willReturn(Optional.empty());
        mockMvc.perform(get("/planning/" + USER_ID + "/current").header("Authorization", BEARER))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("invitar devuelve la planeacion")
    void invite() throws Exception {
        given(service.invite(USER_ID)).willReturn(view("INVITED"));
        mockMvc.perform(post("/planning/" + USER_ID + "/invite").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("INVITED"))
                .andExpect(jsonPath("$.partner.name").value("Sam"));
    }

    @Test
    @DisplayName("con el userId de otra persona, 403 y no se llama al servicio")
    void otherUser() throws Exception {
        mockMvc.perform(post("/planning/99/invite").header("Authorization", BEARER))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("votar con un valor cerrado")
    void vote() throws Exception {
        given(service.vote(USER_ID, 50L, 101L, Vote.LIKE)).willReturn(view("OPTIONS"));
        mockMvc.perform(post("/planning/" + USER_ID + "/50/options/101/vote").header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vote\":\"LIKE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("OPTIONS"));
    }

    @Test
    @DisplayName("un conflicto de la sesion responde 409")
    void conflict() throws Exception {
        given(service.more(eq(USER_ID), eq(50L))).willThrow(new ConflictException("Ajusten los filtros para ver más opciones."));
        mockMvc.perform(post("/planning/" + USER_ID + "/50/options/more").header("Authorization", BEARER))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("un mensaje vacio se rechaza con 400")
    void emptyMessage() throws Exception {
        mockMvc.perform(post("/planning/" + USER_ID + "/50/messages").header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"  \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
