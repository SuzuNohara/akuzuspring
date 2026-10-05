package com.nexus.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.config.SecurityConfig;
import com.nexus.dto.LinkCodeResponse;
import com.nexus.dto.LinkStatusResponse;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.JwtService;
import com.nexus.service.LinkService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guardas de pertenencia de nexus-SEC-04 en {@link LinkController}: las 4 rutas operan sobre el
 * propio vinculo del usuario autenticado, nunca el de un tercero.
 */
@WebMvcTest(LinkController.class)
@Import(SecurityConfig.class)
class LinkControllerTest {

    private static final long USER_ID = 4L;

    private static final long OTHER_USER_ID = 9L;

    private static final String USER_EMAIL = "a@b.co";

    private static final String BEARER = "Bearer test-token";

    @Autowired private MockMvc mockMvc;

    @MockBean private LinkService linkService;

    @MockBean private JwtService jwtService;

    private void authenticateAs(long userId) {
        given(jwtService.parse(anyString())).willReturn(new AuthenticatedUser(userId, USER_EMAIL));
    }

    @Test
    @DisplayName("should return 200 when generating your own link code")
    void shouldReturn200WhenGeneratingYourOwnLinkCode() throws Exception {
        authenticateAs(USER_ID);
        given(linkService.generateLinkCode(USER_ID))
                .willReturn(LinkCodeResponse.builder().code("ABCD").build());

        mockMvc.perform(post("/link/generate/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when generating another user's link code")
    void shouldReturn403AndNotTouchTheServiceWhenGeneratingAnotherUsersLinkCode() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(post("/link/generate/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(linkService);
    }

    @Test
    @DisplayName("should return 200 when establishing a link as yourself")
    void shouldReturn200WhenEstablishingALinkAsYourself() throws Exception {
        authenticateAs(USER_ID);
        given(linkService.establishLink(USER_ID, "ABCD"))
                .willReturn(LinkStatusResponse.builder().hasActiveLink(true).build());

        mockMvc.perform(
                        post("/link/establish/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"ABCD\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when establishing a link as another user")
    void shouldReturn403AndNotTouchTheServiceWhenEstablishingALinkAsAnotherUser() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        post("/link/establish/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"ABCD\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(linkService);
    }

    @Test
    @DisplayName("should return 200 when reading your own link status")
    void shouldReturn200WhenReadingYourOwnLinkStatus() throws Exception {
        authenticateAs(USER_ID);
        given(linkService.getLinkStatus(USER_ID))
                .willReturn(LinkStatusResponse.builder().hasActiveLink(false).build());

        mockMvc.perform(get("/link/status/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when reading another user's link status")
    void shouldReturn403AndNotTouchTheServiceWhenReadingAnotherUsersLinkStatus() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(get("/link/status/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(linkService);
    }

    @Test
    @DisplayName("should return 200 when deleting your own link")
    void shouldReturn200WhenDeletingYourOwnLink() throws Exception {
        authenticateAs(USER_ID);
        given(linkService.deleteLink(USER_ID))
                .willReturn(LinkController.UnlinkResponse.builder().success(true).build());

        mockMvc.perform(delete("/link/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when deleting another user's link")
    void shouldReturn403AndNotTouchTheServiceWhenDeletingAnotherUsersLink() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(delete("/link/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(linkService);
    }
}
