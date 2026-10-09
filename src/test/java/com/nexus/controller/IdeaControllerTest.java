package com.nexus.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.config.SecurityConfig;
import com.nexus.dto.IdeaCategoryResponse;
import com.nexus.dto.IdeaResponse;
import com.nexus.exception.ForbiddenException;
import com.nexus.model.IdeaCategory;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.JwtService;
import com.nexus.service.IdeaService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RF-34 / RN-29 en {@link IdeaController}: cada quien consulta el banco solo con su propio
 * {@code {userId}} (nexus-SEC-04), y sin vinculo activo el servicio lo rechaza con 403.
 */
@WebMvcTest(IdeaController.class)
@Import(SecurityConfig.class)
class IdeaControllerTest {

    private static final long USER_ID = 4L;
    private static final long OTHER_USER_ID = 9L;
    private static final String BEARER = "Bearer test-token";

    @Autowired private MockMvc mockMvc;

    @MockBean private IdeaService ideaService;

    @MockBean private JwtService jwtService;

    private void authenticateAs(long userId) {
        given(jwtService.parse(anyString())).willReturn(new AuthenticatedUser(userId, "a@b.co"));
    }

    @Test
    @DisplayName("should return 401 without a token, so the app can tell an expired session from a forbidden resource")
    void shouldReturn401WithoutAToken() throws Exception {
        mockMvc.perform(get("/ideas/" + USER_ID)).andExpect(status().isUnauthorized());
        verifyNoInteractions(ideaService);
    }

    @Test
    @DisplayName("should return 401 with a token that does not verify")
    void shouldReturn401WithATokenThatDoesNotVerify() throws Exception {
        given(jwtService.parse(anyString())).willThrow(new io.jsonwebtoken.JwtException("expired"));

        mockMvc.perform(get("/ideas/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(ideaService);
    }

    @Test
    @DisplayName("should return 200 with every idea for your own user")
    void shouldReturn200WithEveryIdeaForYourOwnUser() throws Exception {
        authenticateAs(USER_ID);
        given(ideaService.listIdeas(USER_ID, Optional.empty()))
                .willReturn(
                        List.of(
                                IdeaResponse.builder()
                                        .id(1L)
                                        .title("Picnic en el parque")
                                        .category("AIRE_LIBRE")
                                        .categoryLabel("Aire libre")
                                        .build()));

        mockMvc.perform(get("/ideas/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Picnic en el parque"))
                .andExpect(jsonPath("$[0].categoryLabel").value("Aire libre"));
    }

    @Test
    @DisplayName("should pass the parsed category to the service")
    void shouldPassTheParsedCategoryToTheService() throws Exception {
        authenticateAs(USER_ID);
        given(ideaService.listIdeas(USER_ID, Optional.of(IdeaCategory.CULTURA)))
                .willReturn(List.of(IdeaResponse.builder().id(2L).title("Concierto").build()));

        mockMvc.perform(
                        get("/ideas/" + USER_ID)
                                .param("category", "cultura")
                                .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Concierto"));
    }

    @Test
    @DisplayName("should return 400 and not touch the service for an unknown category")
    void shouldReturn400AndNotTouchTheServiceForAnUnknownCategory() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(
                        get("/ideas/" + USER_ID)
                                .param("category", "inventada")
                                .header("Authorization", BEARER))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ideaService);
    }

    @Test
    @DisplayName("should return 403 and not touch the service when reading as another user")
    void shouldReturn403AndNotTouchTheServiceWhenReadingAsAnotherUser() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(get("/ideas/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ideaService);
    }

    @Test
    @DisplayName("should return 403 when the user has no active link (RN-29)")
    void shouldReturn403WhenTheUserHasNoActiveLink() throws Exception {
        authenticateAs(USER_ID);
        given(ideaService.listIdeas(USER_ID, Optional.empty()))
                .willThrow(new ForbiddenException("Necesitas un vínculo activo"));

        mockMvc.perform(get("/ideas/" + USER_ID).header("Authorization", BEARER))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should list the categories for any authenticated user")
    void shouldListTheCategoriesForAnyAuthenticatedUser() throws Exception {
        authenticateAs(USER_ID);
        given(ideaService.listCategories())
                .willReturn(List.of(IdeaCategoryResponse.builder().code("CULTURA").label("Cultura y espectáculos").build()));

        mockMvc.perform(get("/ideas/categories").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CULTURA"));
    }
}
