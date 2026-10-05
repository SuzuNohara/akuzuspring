package com.nexus.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus.config.SecurityConfig;
import com.nexus.dto.AvatarData;
import com.nexus.dto.DeleteAccountResponse;
import com.nexus.dto.UpdateAvatarResponse;
import com.nexus.dto.UpdateProfileResponse;
import com.nexus.entity.Link;
import com.nexus.repository.LinkRepository;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.JwtService;
import com.nexus.security.LinkMembershipGuard;
import com.nexus.service.UserService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guardas de pertenencia de nexus-SEC-04 en {@link ProfileController}: todo endpoint exige que el
 * {@code {userId}} de la URL coincida con el usuario autenticado, salvo {@code GET .../avatar},
 * que tambien lo permite a la pareja vinculada (se muestra en la pantalla de vinculo del
 * frontend).
 *
 * <p>{@link UserService}, {@link JwtService} y {@link LinkRepository} van mockeados: aqui se
 * prueba el cableado de autorizacion, no la logica de negocio (eso ya lo cubre {@code
 * UserServiceTest} si existe) ni la firma del token ({@code JwtServiceTest}).
 */
@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, LinkMembershipGuard.class})
class ProfileControllerTest {

    private static final long USER_ID = 4L;

    private static final long OTHER_USER_ID = 9L;

    private static final String USER_EMAIL = "a@b.co";

    private static final String BEARER = "Bearer test-token";

    @Autowired private MockMvc mockMvc;

    @MockBean private UserService userService;

    @MockBean private JwtService jwtService;

    @MockBean private LinkRepository linkRepository;

    private void authenticateAs(long userId) {
        given(jwtService.parse(anyString())).willReturn(new AuthenticatedUser(userId, USER_EMAIL));
    }

    @Test
    @DisplayName("should return 200 when updating your own profile")
    void shouldReturn200WhenUpdatingYourOwnProfile() throws Exception {
        authenticateAs(USER_ID);
        given(userService.updateProfile(eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .willReturn(new UpdateProfileResponse());

        mockMvc.perform(
                        put("/profile/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"displayName\":\"Ada\",\"email\":\"a@b.co\","
                                                + "\"birthDate\":\"1990-01-01\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when updating another user's profile")
    void shouldReturn403AndNotTouchTheServiceWhenUpdatingAnotherUsersProfile() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        put("/profile/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"displayName\":\"Ada\",\"email\":\"a@b.co\","
                                                + "\"birthDate\":\"1990-01-01\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("should return 200 when updating your own avatar")
    void shouldReturn200WhenUpdatingYourOwnAvatar() throws Exception {
        authenticateAs(USER_ID);
        given(userService.updateAvatar(eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .willReturn(UpdateAvatarResponse.builder().success(true).build());

        mockMvc.perform(
                        put("/profile/" + USER_ID + "/avatar")
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"imageBase64\":\"data:image/png;base64,abc\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when updating another user's avatar")
    void shouldReturn403AndNotTouchTheServiceWhenUpdatingAnotherUsersAvatar() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        put("/profile/" + USER_ID + "/avatar")
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"imageBase64\":\"data:image/png;base64,abc\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("should return 200 when reading your own avatar")
    void shouldReturn200WhenReadingYourOwnAvatar() throws Exception {
        authenticateAs(USER_ID);
        given(userService.getAvatar(USER_ID))
                .willReturn(AvatarData.builder().bytes(new byte[] {1}).mimeType("image/png").build());

        mockMvc.perform(get("/profile/" + USER_ID + "/avatar").header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 200 when reading the linked partner's avatar")
    void shouldReturn200WhenReadingTheLinkedPartnersAvatar() throws Exception {
        authenticateAs(OTHER_USER_ID);
        given(linkRepository.findActiveLinkBetweenUsers(OTHER_USER_ID, USER_ID))
                .willReturn(Optional.of(org.mockito.Mockito.mock(Link.class)));
        given(userService.getAvatar(USER_ID))
                .willReturn(AvatarData.builder().bytes(new byte[] {1}).mimeType("image/png").build());

        mockMvc.perform(get("/profile/" + USER_ID + "/avatar").header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when reading a stranger's avatar")
    void shouldReturn403AndNotTouchTheServiceWhenReadingAStrangersAvatar() throws Exception {
        authenticateAs(OTHER_USER_ID);
        given(linkRepository.findActiveLinkBetweenUsers(OTHER_USER_ID, USER_ID))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/profile/" + USER_ID + "/avatar").header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("should return 200 when deleting your own avatar")
    void shouldReturn200WhenDeletingYourOwnAvatar() throws Exception {
        authenticateAs(USER_ID);
        given(userService.deleteAvatar(USER_ID))
                .willReturn(UpdateAvatarResponse.builder().success(true).build());

        mockMvc.perform(delete("/profile/" + USER_ID + "/avatar").header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when deleting another user's avatar")
    void shouldReturn403AndNotTouchTheServiceWhenDeletingAnotherUsersAvatar() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(delete("/profile/" + USER_ID + "/avatar").header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("should return 200 when deleting your own account")
    void shouldReturn200WhenDeletingYourOwnAccount() throws Exception {
        authenticateAs(USER_ID);
        given(userService.deleteAccount(eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .willReturn(
                        DeleteAccountResponse.builder()
                                .message("Cuenta eliminada")
                                .deletedEmail("a@b.co")
                                .build());

        mockMvc.perform(
                        delete("/profile/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"Secreto123!\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when deleting another user's account")
    void shouldReturn403AndNotTouchTheServiceWhenDeletingAnotherUsersAccount() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        delete("/profile/" + USER_ID)
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"Secreto123!\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("should return 200 when registering your own fcm token")
    void shouldReturn200WhenRegisteringYourOwnFcmToken() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/profile/" + USER_ID + "/fcm-token")
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fcmToken\":\"tok\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 403 and not touch the service when registering another user's fcm token")
    void shouldReturn403AndNotTouchTheServiceWhenRegisteringAnotherUsersFcmToken() throws Exception {
        authenticateAs(OTHER_USER_ID);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/profile/" + USER_ID + "/fcm-token")
                                .header("Authorization", BEARER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fcmToken\":\"tok\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }
}
