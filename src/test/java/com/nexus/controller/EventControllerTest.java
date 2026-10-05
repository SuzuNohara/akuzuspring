package com.nexus.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.config.SecurityConfig;
import com.nexus.dto.CreateEventResponse;
import com.nexus.dto.EventResponse;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.JwtService;
import com.nexus.service.EventService;
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
 * Guardas de pertenencia de nexus-SEC-04 en {@link EventController}: en las 9 rutas (salvo
 * {@code /health}), el {@code {userId}} de la URL es quien EJECUTA la accion (crear, aprobar,
 * rechazar, editar, eliminar...) y debe coincidir con el usuario autenticado -- que ese
 * {@code userId} tenga permiso sobre el evento en si (p. ej. aprobar un evento de la pareja) lo
 * sigue verificando {@link EventService}, esta guarda solo evita que alguien actue
 * suplantando a otro usuario.
 */
@WebMvcTest(EventController.class)
@Import(SecurityConfig.class)
class EventControllerTest {

    private static final long USER_ID = 4L;

    private static final long OTHER_USER_ID = 9L;

    private static final long EVENT_ID = 100L;

    private static final String USER_EMAIL = "a@b.co";

    private static final String BEARER = "Bearer test-token";

    private static final String VALID_CREATE_BODY =
            "{\"title\":\"Cena\",\"startDateTime\":\"2026-01-01T20:00:00-06:00\","
                    + "\"endDateTime\":\"2026-01-01T22:00:00-06:00\"}";

    @Autowired private MockMvc mockMvc;

    @MockBean private EventService eventService;

    @MockBean private JwtService jwtService;

    private void authenticateAs(long userId) {
        given(jwtService.parse(anyString())).willReturn(new AuthenticatedUser(userId, USER_EMAIL));
    }

    @Test
    @DisplayName("should return 201 when creating an event as yourself")
    void shouldReturn201WhenCreatingAnEventAsYourself() throws Exception {
        authenticateAs(USER_ID);
        given(eventService.createEvent(
                        org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .willReturn(CreateEventResponse.builder().success(true).build());

        mockMvc.perform(
                        post("/events/create/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_CREATE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when creating an event as another user")
    void shouldReturn403AndNotTouchTheServiceWhenCreatingAnEventAsAnotherUser() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        post("/events/create/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_CREATE_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when listing another user's events")
    void shouldReturn403AndNotTouchTheServiceWhenListingAnotherUsersEvents() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(get("/events/user/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when listing another user's pending approvals")
    void shouldReturn403AndNotTouchTheServiceWhenListingAnotherUsersPendingApprovals()
            throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        get("/events/user/" + USER_ID + "/pending-approval")
                                .header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when counting another user's pending approvals")
    void shouldReturn403AndNotTouchTheServiceWhenCountingAnotherUsersPendingApprovals()
            throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        get("/events/user/" + USER_ID + "/pending-count")
                                .header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("should return 200 when approving an event as yourself")
    void shouldReturn200WhenApprovingAnEventAsYourself() throws Exception {
        authenticateAs(USER_ID);
        given(eventService.approveEvent(USER_ID, EVENT_ID)).willReturn(EventResponse.builder().build());

        mockMvc.perform(
                        post("/events/" + EVENT_ID + "/approve/" + USER_ID)
                                .header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when approving an event as another user")
    void shouldReturn403AndNotTouchTheServiceWhenApprovingAnEventAsAnotherUser() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        post("/events/" + EVENT_ID + "/approve/" + USER_ID)
                                .header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when updating an event as another user")
    void shouldReturn403AndNotTouchTheServiceWhenUpdatingAnEventAsAnotherUser() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        put("/events/" + EVENT_ID + "/user/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when rejecting an event as another user")
    void shouldReturn403AndNotTouchTheServiceWhenRejectingAnEventAsAnotherUser() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        post("/events/" + EVENT_ID + "/reject/" + USER_ID)
                                .header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when deleting an event as another user")
    void shouldReturn403AndNotTouchTheServiceWhenDeletingAnEventAsAnotherUser() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        delete("/events/" + EVENT_ID + "/user/" + USER_ID)
                                .header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when adding an exception as another user")
    void shouldReturn403AndNotTouchTheServiceWhenAddingAnExceptionAsAnotherUser() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        post("/events/" + EVENT_ID + "/exceptions")
                                .header("Authorization", BEARER)
                                .param("exceptionDate", "2026-01-01")
                                .param("userId", String.valueOf(USER_ID)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(eventService);
    }
}
