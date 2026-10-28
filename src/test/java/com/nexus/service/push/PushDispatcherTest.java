package com.nexus.service.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nexus.config.PushProperties;
import com.nexus.entity.User;
import com.nexus.integration.expo.ExpoPushClient;
import com.nexus.integration.expo.ExpoPushClient.PushMessage;
import com.nexus.integration.expo.ExpoPushClient.PushResult;
import com.nexus.repository.UserRepository;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Cuando sale la push de un aviso y que pasa con un token vencido ([push-avisos] U2). */
class PushDispatcherTest {

    private static final long USER_ID = 4L;
    private static final String TOKEN = "ExponentPushToken[abc]";
    private static final PushRequested EVENT = new PushRequested(
            USER_ID, "PROPOSAL", 9L, "Sam te propuso una cita", "Cena el viernes.", Map.of());

    private final ExpoPushClient client = mock(ExpoPushClient.class);
    private final UserRepository users = mock(UserRepository.class);

    private PushDispatcher dispatcher(boolean enabled) {
        return new PushDispatcher(
                new PushProperties(enabled, URI.create("http://expo.test"), Duration.ofSeconds(5)), client, users);
    }

    private void userHasToken(String token) {
        given(users.findActiveById(USER_ID))
                .willReturn(Optional.of(User.builder().id(USER_ID).fcmToken(token).build()));
    }

    @Test
    @DisplayName("should not look anything up nor send when push is disabled")
    void shouldNotLookAnythingUpNorSendWhenPushIsDisabled() {
        dispatcher(false).on(EVENT);

        verifyNoInteractions(users, client);
    }

    @Test
    @DisplayName("should not send when the user has no token")
    void shouldNotSendWhenTheUserHasNoToken() {
        userHasToken(null);

        dispatcher(true).on(EVENT);

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("should not send when the user no longer exists")
    void shouldNotSendWhenTheUserNoLongerExists() {
        given(users.findActiveById(USER_ID)).willReturn(Optional.empty());

        dispatcher(true).on(EVENT);

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("should send once to the user's token with the texts and the route data")
    void shouldSendOnceToTheUsersTokenWithTheTextsAndTheRouteData() {
        userHasToken(TOKEN);
        given(client.send(any())).willReturn(PushResult.SENT);

        dispatcher(true).on(EVENT);

        ArgumentCaptor<PushMessage> sent = ArgumentCaptor.forClass(PushMessage.class);
        verify(client).send(sent.capture());
        assertThat(sent.getValue().to()).isEqualTo(TOKEN);
        assertThat(sent.getValue().title()).isEqualTo("Sam te propuso una cita");
        assertThat(sent.getValue().body()).isEqualTo("Cena el viernes.");
        assertThat(sent.getValue().data())
                .containsEntry("type", "PROPOSAL")
                .containsEntry("route", "/(planning)/session")
                .containsEntry("sessionId", "9");
        verify(users, never()).clearTokenIfMatches(any(), any());
    }

    @Test
    @DisplayName("should clear the token only if it is still the same when Expo says it is not registered")
    void shouldClearTheTokenOnlyIfItIsStillTheSameWhenExpoSaysItIsNotRegistered() {
        userHasToken(TOKEN);
        given(client.send(any())).willReturn(PushResult.TOKEN_INVALID);

        dispatcher(true).on(EVENT);

        verify(users).clearTokenIfMatches(USER_ID, TOKEN);
    }

    @Test
    @DisplayName("should keep the token when the send just failed")
    void shouldKeepTheTokenWhenTheSendJustFailed() {
        userHasToken(TOKEN);
        given(client.send(any())).willReturn(PushResult.FAILED);

        dispatcher(true).on(EVENT);

        verify(users, never()).clearTokenIfMatches(any(), any());
    }
}
