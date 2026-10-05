package com.nexus.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.config.SecurityConfig;
import com.nexus.dto.QuestionnaireStatusDTO;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.JwtService;
import com.nexus.service.PreferenceService;
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
 * Guardas de pertenencia de nexus-SEC-04 en {@link PreferenceController}: las rutas con
 * {@code {userId}} exigen ser el dueno. {@code /categories} y {@code /category/{categoryId}} son
 * catalogo global, sin dueno, y no llevan guarda.
 */
@WebMvcTest(PreferenceController.class)
@Import(SecurityConfig.class)
class PreferenceControllerTest {

    private static final long USER_ID = 4L;

    private static final long OTHER_USER_ID = 9L;

    private static final String USER_EMAIL = "a@b.co";

    private static final String BEARER = "Bearer test-token";

    @Autowired private MockMvc mockMvc;

    @MockBean private PreferenceService preferenceService;

    @MockBean private JwtService jwtService;

    private void authenticateAs(long userId) {
        given(jwtService.parse(anyString())).willReturn(new AuthenticatedUser(userId, USER_EMAIL));
    }

    @Test
    @DisplayName("should return 200 when reading your own questionnaire status")
    void shouldReturn200WhenReadingYourOwnQuestionnaireStatus() throws Exception {
        authenticateAs(USER_ID);
        given(preferenceService.getQuestionnaireStatus(USER_ID))
                .willReturn(QuestionnaireStatusDTO.builder().completed(true).build());

        mockMvc.perform(get("/preferences/status/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when reading another user's questionnaire status")
    void shouldReturn403AndNotTouchTheServiceWhenReadingAnotherUsersQuestionnaireStatus()
            throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(get("/preferences/status/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(preferenceService);
    }

    @Test
    @DisplayName("should return 200 when reading your own preferences")
    void shouldReturn200WhenReadingYourOwnPreferences() throws Exception {
        authenticateAs(USER_ID);
        given(preferenceService.getUserPreferences(USER_ID)).willReturn(List.of());

        mockMvc.perform(get("/preferences/user/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when reading another user's preferences")
    void shouldReturn403AndNotTouchTheServiceWhenReadingAnotherUsersPreferences() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(get("/preferences/user/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(preferenceService);
    }

    @Test
    @DisplayName("should return 200 when saving your own preferences")
    void shouldReturn200WhenSavingYourOwnPreferences() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(
                        post("/preferences/user/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"preferences\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when saving another user's preferences")
    void shouldReturn403AndNotTouchTheServiceWhenSavingAnotherUsersPreferences() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        post("/preferences/user/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"preferences\":[]}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(preferenceService);
    }
}
