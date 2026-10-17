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
import com.nexus.dto.BoroughResponse;
import com.nexus.dto.RecommendationBatch;
import com.nexus.exception.AgentUnavailableException;
import com.nexus.exception.ConflictException;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.JwtService;
import com.nexus.service.recommendation.RecommendationService;
import java.util.List;
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

/** U9 - {@link RecommendationController}: cada quien solo con su {@code {userId}}; errores 400/409/503. */
@WebMvcTest(RecommendationController.class)
@Import(SecurityConfig.class)
class RecommendationControllerTest {

    private static final long USER_ID = 1L;
    private static final String BEARER = "Bearer test-token";
    private static final String BODY = """
            {"zoneType":"NONE","scope":"ANY","maxCostMxn":1500,
             "slotStart":"2026-10-18T01:00:00Z","slotEnd":"2026-10-18T03:00:00Z"}""";

    @Autowired private MockMvc mockMvc;
    @MockBean private RecommendationService service;
    @MockBean private JwtService jwtService;

    @BeforeEach
    void authenticate() {
        given(jwtService.parse(anyString())).willReturn(new AuthenticatedUser(USER_ID, "a@b.co"));
    }

    @Test
    @DisplayName("sin token, 401")
    void requiresToken() throws Exception {
        mockMvc.perform(get("/recommendations/boroughs")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A20: lista de alcaldias")
    void boroughs() throws Exception {
        given(service.boroughs()).willReturn(List.of(new BoroughResponse("COYOACAN", "Coyoacán")));
        mockMvc.perform(get("/recommendations/boroughs").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("COYOACAN"));
    }

    @Test
    @DisplayName("A1: pedir recomendaciones con el propio usuario")
    void request() throws Exception {
        given(service.request(eq(USER_ID), any()))
                .willReturn(new RecommendationBatch(100L, List.of(), Set.of(), "CATALOG", true));
        mockMvc.perform(post("/recommendations/" + USER_ID).header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(100))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    @DisplayName("A2: pedir con el id de otra persona, 403")
    void otherUser() throws Exception {
        mockMvc.perform(post("/recommendations/9").header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("A8: presupuesto fuera de rango, 400")
    void invalidBody() throws Exception {
        mockMvc.perform(post("/recommendations/" + USER_ID).header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("1500", "20000")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A12: sin mas opciones, 409")
    void conflict() throws Exception {
        given(service.more(USER_ID, 100L)).willThrow(new ConflictException("Ajusta los filtros"));
        mockMvc.perform(post("/recommendations/" + USER_ID + "/requests/100/more").header("Authorization", BEARER))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A19: el servicio de busqueda no responde, 503")
    void unavailable() throws Exception {
        given(service.request(eq(USER_ID), any())).willThrow(new AgentUnavailableException("caido"));
        mockMvc.perform(post("/recommendations/" + USER_ID).header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("descartar e invitar delegan con el propio usuario")
    void discardAndInvite() throws Exception {
        mockMvc.perform(post("/recommendations/" + USER_ID + "/items/501/discard").header("Authorization", BEARER))
                .andExpect(status().isOk());
        mockMvc.perform(post("/recommendations/" + USER_ID + "/items/501/invite").header("Authorization", BEARER))
                .andExpect(status().isOk());
        mockMvc.perform(post("/recommendations/9/items/501/invite").header("Authorization", BEARER))
                .andExpect(status().isForbidden());
    }
}
