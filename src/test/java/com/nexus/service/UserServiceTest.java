package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nexus.entity.User;
import com.nexus.entity.VerificationToken;
import com.nexus.repository.LinkCodeRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.repository.UserProfileRepository;
import com.nexus.repository.UserRepository;
import com.nexus.repository.VerificationTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/** RN-02: el codigo de verificacion de correo tiene una vigencia de 15 minutos. */
class UserServiceTest {

    private static final String EMAIL = "ana@example.com";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final VerificationTokenRepository verificationTokenRepository =
            mock(VerificationTokenRepository.class);

    private final UserService service =
            new UserService(
                    userRepository,
                    mock(UserProfileRepository.class),
                    verificationTokenRepository,
                    mock(LinkRepository.class),
                    mock(LinkCodeRepository.class),
                    mock(PasswordEncoder.class),
                    mock(EmailService.class));

    @Test
    @DisplayName("should issue an email verification code that expires in 15 minutes (RN-02)")
    void shouldIssueAnEmailVerificationCodeThatExpiresIn15Minutes() {
        User unverified = User.builder().id(4L).email(EMAIL).emailConfirmed(false).build();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(unverified));
        given(verificationTokenRepository.save(any(VerificationToken.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Instant before = Instant.now();
        service.resendVerificationCode(EMAIL);
        Instant after = Instant.now();

        ArgumentCaptor<VerificationToken> saved = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt())
                .isBetween(before.plus(Duration.ofMinutes(15)), after.plus(Duration.ofMinutes(15)));
    }
}
