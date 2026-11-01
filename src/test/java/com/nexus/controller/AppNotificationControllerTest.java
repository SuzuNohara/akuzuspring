package com.nexus.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.config.SecurityConfig;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.JwtService;
import com.nexus.service.AppNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** [ux-ajustes] D8 - "Limpiar" en la campanita: cada quien borra solo sus propios avisos leidos. */
@WebMvcTest(AppNotificationController.class)
@Import(SecurityConfig.class)
class AppNotificationControllerTest {

    private static final String BEARER = "Bearer test-token";

    @Autowired private MockMvc mockMvc;

    @MockBean private AppNotificationService service;

    @MockBean private JwtService jwtService;

    private void authenticateAs(long userId) {
        given(jwtService.parse(anyString())).willReturn(new AuthenticatedUser(userId, "a@b.co"));
    }

    @Test
    @DisplayName("borra los avisos leidos propios y dice cuantos se fueron")
    void clearsOwnReadNotices() throws Exception {
        authenticateAs(4L);
        given(service.clearRead(4L)).willReturn(8);

        mockMvc.perform(delete("/notifications/4/read").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(8));
    }

    @Test
    @DisplayName("no deja borrar los avisos de otra persona")
    void forbidsOtherPeoplesNotices() throws Exception {
        authenticateAs(4L);

        mockMvc.perform(delete("/notifications/9/read").header("Authorization", BEARER))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
