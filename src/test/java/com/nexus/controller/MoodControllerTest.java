package com.nexus.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.config.SecurityConfig;
import com.nexus.dto.MoodEntryResponse;
import com.nexus.dto.MoodHistoryResponse;
import com.nexus.dto.MoodOptionResponse;
import com.nexus.dto.RecordMoodResponse;
import com.nexus.exception.ConflictException;
import com.nexus.service.MoodService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Superficie HTTP de {@link MoodController} (nexus-EMO-01). {@link MoodService} va mockeado: aquí
 * se prueba el cableado, los códigos de estado y la regla de seguridad provisional ({@code
 * permitAll} hasta AUTH-02). La persistencia la cubren los {@code *IT}.
 */
@WebMvcTest(MoodController.class)
@Import(SecurityConfig.class)
class MoodControllerTest {

    private static final long USER_ID = 4L;

    @Autowired private MockMvc mockMvc;

    @MockBean private MoodService moodService;

    @Test
    @DisplayName("should return options without valence/arousal coordinates")
    void shouldReturnOptionsWithoutCoordinates() throws Exception {
        given(moodService.listOptions())
                .willReturn(List.of(new MoodOptionResponse("CALM", "Tranquilo", "😌", 3)));

        mockMvc.perform(get("/mood/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CALM"))
                .andExpect(jsonPath("$[0].sortOrder").value(3))
                .andExpect(jsonPath("$[0].valence").doesNotExist())
                .andExpect(jsonPath("$[0].arousal").doesNotExist());
    }

    @Test
    @DisplayName("should return 204 when there is no entry today")
    void shouldReturn204WhenNoEntryToday() throws Exception {
        given(moodService.findToday(USER_ID)).willReturn(Optional.empty());

        mockMvc.perform(get("/mood/today/{userId}", USER_ID)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("should return 200 with today's entry")
    void shouldReturnTodayEntry() throws Exception {
        given(moodService.findToday(USER_ID))
                .willReturn(
                        Optional.of(
                                new MoodEntryResponse(
                                        LocalDate.of(2026, 10, 8),
                                        "CALM",
                                        "Tranquilo",
                                        "😌",
                                        Instant.parse("2026-10-08T19:02:11.123Z"))));

        mockMvc.perform(get("/mood/today/{userId}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-08"))
                .andExpect(jsonPath("$.code").value("CALM"))
                .andExpect(jsonPath("$.label").value("Tranquilo"))
                .andExpect(jsonPath("$.emoji").value("😌"));
    }

    @Test
    @DisplayName("should return 200 with the replaced flag when recording")
    void shouldReturn200WithReplacedFlagWhenRecording() throws Exception {
        given(moodService.recordToday(USER_ID, "CALM"))
                .willReturn(
                        new RecordMoodResponse(
                                new MoodEntryResponse(
                                        LocalDate.of(2026, 10, 8), "CALM", "Tranquilo", "😌", null),
                                false));

        mockMvc.perform(
                        put("/mood/today/{userId}", USER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"CALM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replaced").value(false))
                .andExpect(jsonPath("$.entry.code").value("CALM"));
    }

    @Test
    @DisplayName("should return 400 and skip the service when the code is lowercase")
    void shouldReturn400AndSkipServiceWhenCodeIsLowercase() throws Exception {
        mockMvc.perform(
                        put("/mood/today/{userId}", USER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"calm\"}"))
                .andExpect(status().isBadRequest());

        then(moodService).should(never()).recordToday(anyLong(), any());
    }

    @Test
    @DisplayName("should return 409 when the service reports a conflict")
    void shouldReturn409WhenServiceReportsConflict() throws Exception {
        given(moodService.recordToday(USER_ID, "CALM"))
                .willThrow(new ConflictException("Ya existe un registro de hoy"));

        mockMvc.perform(
                        put("/mood/today/{userId}", USER_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"CALM\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("should pass before and limit to the service")
    void shouldPassBeforeAndLimitToService() throws Exception {
        given(moodService.history(eq(USER_ID), any(), eq(30)))
                .willReturn(
                        new MoodHistoryResponse(
                                LocalDate.of(2026, 10, 8), List.of(), List.of(), null));

        mockMvc.perform(
                        get("/mood/history/{userId}", USER_ID)
                                .param("before", "2026-08-01")
                                .param("limit", "30"))
                .andExpect(status().isOk());

        then(moodService)
                .should()
                .history(USER_ID, Optional.of(LocalDate.of(2026, 8, 1)), 30);
    }

    @Test
    @DisplayName("should return 400 when before is malformed")
    void shouldReturn400WhenBeforeIsMalformed() throws Exception {
        mockMvc.perform(get("/mood/history/{userId}", USER_ID).param("before", "ayer"))
                .andExpect(status().isBadRequest());

        then(moodService).should(never()).history(anyLong(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("should be reachable without a token until AUTH-02")
    void shouldBeReachableWithoutTokenUntilAuth02() throws Exception {
        given(moodService.listOptions()).willReturn(List.of());

        mockMvc.perform(get("/mood/options"))
                .andExpect(status().isOk()); // ni 401 ni 403: /mood/** es permitAll de momento
    }

    @Test
    @Disabled("nexus-AUTH-03: cuando exista el principal, pathUserId != principal → 403")
    @DisplayName("should return 403 when the path user differs from the principal")
    void shouldReturn403WhenPathUserDiffersFromPrincipal() throws Exception {
        mockMvc.perform(get("/mood/today/{userId}", 99L)).andExpect(status().isForbidden());
    }
}
