package com.nexus.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.config.SecurityConfig;
import com.nexus.dto.OnboardingProgressResponse;
import com.nexus.dto.OnboardingProgressResponse.Couple;
import com.nexus.dto.OnboardingProgressResponse.Mine;
import com.nexus.dto.OnboardingProgressResponse.Partner;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.JwtService;
import com.nexus.service.OnboardingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** [primeros-pasos] B-10..B-13: cada quien pide solo su propio avance y la pareja viaja sin detalles. */
@WebMvcTest(OnboardingController.class)
@Import(SecurityConfig.class)
class OnboardingControllerTest {

    private static final long USER_ID = 4L;
    private static final long OTHER_USER_ID = 9L;
    private static final String BEARER = "Bearer test-token";
    private static final String PATH = "/onboarding/" + USER_ID + "/progress";

    @Autowired private MockMvc mockMvc;

    @MockBean private OnboardingService onboardingService;

    @MockBean private JwtService jwtService;

    private void authenticateAs(long userId) {
        given(jwtService.parse(anyString())).willReturn(new AuthenticatedUser(userId, "a@b.co"));
    }

    private static OnboardingProgressResponse progress() {
        return new OnboardingProgressResponse(
                new Mine(true, true, false, true),
                new Partner("Sam", true, true, false),
                new Couple(true, false));
    }

    @Test
    @DisplayName("B-10 devuelve 200 con el avance propio")
    void returnsOwnProgress() throws Exception {
        authenticateAs(USER_ID);
        given(onboardingService.progressFor(USER_ID)).willReturn(progress());

        mockMvc.perform(get(PATH).header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.questionnaire").value(true))
                .andExpect(jsonPath("$.me.calendar").value(false))
                .andExpect(jsonPath("$.partner.name").value("Sam"))
                .andExpect(jsonPath("$.couple.firstRecommendation").value(true))
                .andExpect(jsonPath("$.couple.firstRatedDate").value(false));
    }

    @Test
    @DisplayName("B-11 pedir el avance de otra persona da 403 y no consulta nada")
    void forbidsOthersProgress() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(get(PATH).header("Authorization", BEARER)).andExpect(status().isForbidden());

        verifyNoInteractions(onboardingService);
    }

    @Test
    @DisplayName("B-12 sin token da 401")
    void requiresToken() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        verifyNoInteractions(onboardingService);
    }

    @Test
    @DisplayName("B-13 de la pareja solo viajan nombre y tres si/no")
    void partnerCarriesOnlyFourFields() throws Exception {
        authenticateAs(USER_ID);
        given(onboardingService.progressFor(USER_ID)).willReturn(progress());

        mockMvc.perform(get(PATH).header("Authorization", BEARER))
                .andExpect(jsonPath("$.partner", aMapWithSize(4)))
                .andExpect(jsonPath("$.partner.questionnaire").value(true))
                .andExpect(jsonPath("$.partner.calendar").value(true))
                .andExpect(jsonPath("$.partner.schedule").value(false));
    }
}
