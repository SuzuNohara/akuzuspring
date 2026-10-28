package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nexus.entity.User;
import com.nexus.exception.BadRequestException;
import com.nexus.repository.LinkCodeRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.repository.UserProfileRepository;
import com.nexus.repository.UserRepository;
import com.nexus.repository.VerificationTokenRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Token de push del telefono: uno por telefono y se quita al cerrar sesion ([push-avisos] U6). */
class UserServiceTokenTest {

    private static final long USER_ID = 4L;
    private static final String TOKEN = "ExponentPushToken[abc]";

    private final UserRepository userRepository = mock(UserRepository.class);

    private final UserService service = new UserService(
            userRepository,
            mock(UserProfileRepository.class),
            mock(VerificationTokenRepository.class),
            mock(LinkRepository.class),
            mock(LinkCodeRepository.class),
            mock(PasswordEncoder.class),
            mock(EmailService.class));

    @Test
    @DisplayName("should take the token away from other accounts before saving it on this one")
    void shouldTakeTheTokenAwayFromOtherAccountsBeforeSavingItOnThisOne() {
        User user = User.builder().id(USER_ID).email("ana@example.com").build();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        service.updateFCMToken(USER_ID, TOKEN);

        InOrder order = inOrder(userRepository);
        order.verify(userRepository).clearTokenFromOthers(TOKEN, USER_ID);
        order.verify(userRepository).save(user);
        assertThat(user.getFcmToken()).isEqualTo(TOKEN);
    }

    @Test
    @DisplayName("should not touch any token when the user does not exist")
    void shouldNotTouchAnyTokenWhenTheUserDoesNotExist() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateFCMToken(USER_ID, TOKEN))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).clearTokenFromOthers(any(), any());
    }

    @Test
    @DisplayName("should clear the token of the user on logout")
    void shouldClearTheTokenOfTheUserOnLogout() {
        service.clearFCMToken(USER_ID);

        verify(userRepository).clearToken(USER_ID);
    }
}
