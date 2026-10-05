package com.nexus.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.config.SecurityConfig;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.JwtService;
import com.nexus.service.ExternalCalendarService;
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
 * Guardas de pertenencia de nexus-SEC-04 en {@link ExternalCalendarController}: todas las rutas
 * con {@code {userId}} exigen ser el dueno; {@code /mutual-availability} exige ser uno de los dos
 * participantes ({@code user1Id}/{@code user2Id}).
 *
 * <p>No repite un caso 200 por endpoint (eso lo cubren los {@code *IT} si existen) -- se enfoca en
 * probar que la guarda nueva se ejecuta antes que el {@code catch (Exception)} generico del
 * controller, para que un 403 no se convierta en 500.
 */
@WebMvcTest(ExternalCalendarController.class)
@Import(SecurityConfig.class)
class ExternalCalendarControllerTest {

    private static final long USER_ID = 4L;

    private static final long OTHER_USER_ID = 9L;

    private static final String USER_EMAIL = "a@b.co";

    private static final String BEARER = "Bearer test-token";

    private static final String VALID_LINK_BODY =
            "{\"deviceCalendarId\":\"cal-1\",\"calendarName\":\"Personal\","
                    + "\"syncEnabled\":true,\"privacyMode\":\"BUSY_ONLY\"}";

    @Autowired private MockMvc mockMvc;

    @MockBean private ExternalCalendarService externalCalendarService;

    @MockBean private JwtService jwtService;

    private void authenticateAs(long userId) {
        given(jwtService.parse(anyString())).willReturn(new AuthenticatedUser(userId, USER_EMAIL));
    }

    @Test
    @DisplayName("should return 200 when linking your own calendar")
    void shouldReturn200WhenLinkingYourOwnCalendar() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(
                        post("/calendars/external/link/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_LINK_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when linking another user's calendar")
    void shouldReturn403AndNotTouchTheServiceWhenLinkingAnotherUsersCalendar() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        post("/calendars/external/link/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_LINK_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(externalCalendarService);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when unlinking another user's calendar")
    void shouldReturn403AndNotTouchTheServiceWhenUnlinkingAnotherUsersCalendar() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        delete("/calendars/external/unlink/" + USER_ID + "/cal-1")
                                .header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(externalCalendarService);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when reading another user's calendars")
    void shouldReturn403AndNotTouchTheServiceWhenReadingAnotherUsersCalendars() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        get("/calendars/external/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(externalCalendarService);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when updating another user's calendar settings")
    void shouldReturn403AndNotTouchTheServiceWhenUpdatingAnotherUsersCalendarSettings()
            throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        patch("/calendars/external/" + USER_ID + "/cal-1")
                                .header("Authorization", BEARER)
                                .param("syncEnabled", "true"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(externalCalendarService);
    }

    @Test
    @DisplayName("should return 200 when syncing your own events")
    void shouldReturn200WhenSyncingYourOwnEvents() throws Exception {
        authenticateAs(USER_ID);
        given(externalCalendarService.syncEvents(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.anyList()))
                .willReturn(java.util.Map.of("synced", 0));

        mockMvc.perform(
                        post("/calendars/external/sync/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("[]"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when syncing another user's events")
    void shouldReturn403AndNotTouchTheServiceWhenSyncingAnotherUsersEvents() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        post("/calendars/external/sync/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("[]"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(externalCalendarService);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when reading another user's external events")
    void shouldReturn403AndNotTouchTheServiceWhenReadingAnotherUsersExternalEvents()
            throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        get("/calendars/external/events/" + USER_ID)
                                .header("Authorization", BEARER)
                                .param("startDate", "2026-01-01T00:00:00Z")
                                .param("endDate", "2026-01-02T00:00:00Z"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(externalCalendarService);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when finding another user's free slots")
    void shouldReturn403AndNotTouchTheServiceWhenFindingAnotherUsersFreeSlots() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        get("/calendars/external/availability/" + USER_ID)
                                .header("Authorization", BEARER)
                                .param("startDate", "2026-01-01T00:00:00Z")
                                .param("endDate", "2026-01-02T00:00:00Z"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(externalCalendarService);
    }

    @Test
    @DisplayName("should return 200 when a participant requests mutual availability")
    void shouldReturn200WhenAParticipantRequestsMutualAvailability() throws Exception {
        authenticateAs(USER_ID);
        given(
                        externalCalendarService.findMutualAvailability(
                                org.mockito.ArgumentMatchers.eq(USER_ID),
                                org.mockito.ArgumentMatchers.eq(OTHER_USER_ID),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.anyInt()))
                .willReturn(List.of());

        mockMvc.perform(
                        get("/calendars/external/mutual-availability")
                                .header("Authorization", BEARER)
                                .param("user1Id", String.valueOf(USER_ID))
                                .param("user2Id", String.valueOf(OTHER_USER_ID))
                                .param("startDate", "2026-01-01T00:00:00Z")
                                .param("endDate", "2026-01-02T00:00:00Z"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when neither id is the authenticated user")
    void shouldReturn403AndNotTouchTheServiceWhenNeitherIdIsTheAuthenticatedUser() throws Exception {
        authenticateAs(999L);

        mockMvc.perform(
                        get("/calendars/external/mutual-availability")
                                .header("Authorization", BEARER)
                                .param("user1Id", String.valueOf(USER_ID))
                                .param("user2Id", String.valueOf(OTHER_USER_ID))
                                .param("startDate", "2026-01-01T00:00:00Z")
                                .param("endDate", "2026-01-02T00:00:00Z"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(externalCalendarService);
    }
}
