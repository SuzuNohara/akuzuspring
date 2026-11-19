package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nexus.dto.RegisterRequest;
import com.nexus.entity.User;
import com.nexus.entity.VerificationToken;
import com.nexus.exception.BadRequestException;
import com.nexus.model.AccountStatus;
import com.nexus.repository.LinkCodeRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.repository.UserProfileRepository;
import com.nexus.repository.UserRepository;
import com.nexus.repository.VerificationTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Reglas de la cuenta: registro (RN-01, RN-23, RN-25, RN-26), verificacion de correo (RN-02) e
 * inicio de sesion (RN-03).
 */
class UserServiceTest {

    private static final String EMAIL = "ana@example.com";
    private static final String VALID_PASSWORD = "Segura#2026";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final VerificationTokenRepository verificationTokenRepository =
            mock(VerificationTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private final UserService service =
            new UserService(
                    userRepository,
                    mock(UserProfileRepository.class),
                    verificationTokenRepository,
                    mock(LinkRepository.class),
                    mock(LinkCodeRepository.class),
                    passwordEncoder,
                    mock(EmailService.class));

    private static RegisterRequest.RegisterRequestBuilder validRegistration() {
        return RegisterRequest.builder()
                .email(EMAIL)
                .password(VALID_PASSWORD)
                .confirmPassword(VALID_PASSWORD)
                .displayName("Ana")
                .nickname("Ani")
                .birthDate(LocalDate.now().minusYears(20))
                .termsAccepted(true);
    }

    private static User.UserBuilder verifiedUser() {
        return User.builder()
                .id(4L)
                .email(EMAIL)
                .passwordHash("HASH")
                .emailConfirmed(true)
                .failedLoginAttempts(0)
                .accountStatus(AccountStatus.ACTIVE);
    }

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

    @Test
    @DisplayName("should reject a registration with a password that breaks the policy (RN-01)")
    void shouldRejectARegistrationWithAPasswordThatBreaksThePolicy() {
        RegisterRequest request =
                validRegistration().password("sinmayuscula1#").confirmPassword("sinmayuscula1#").build();

        assertThatThrownBy(() -> service.registerUser(request))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("should reject a registration from someone under 18 (RN-23)")
    void shouldRejectARegistrationFromSomeoneUnder18() {
        RegisterRequest request =
                validRegistration().birthDate(LocalDate.now().minusYears(17)).build();

        assertThatThrownBy(() -> service.registerUser(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Nexus es para mayores de 18 años.");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("should reject a registration that does not accept the terms (RN-26)")
    void shouldRejectARegistrationThatDoesNotAcceptTheTerms() {
        RegisterRequest request = validRegistration().termsAccepted(false).build();

        assertThatThrownBy(() -> service.registerUser(request))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("should reject a registration with an email already in use (RN-25)")
    void shouldRejectARegistrationWithAnEmailAlreadyInUse() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> service.registerUser(validRegistration().build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Este correo ya tiene una cuenta registrada.");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("should treat an email with capitals as the one already in use (RN-25)")
    void shouldTreatAnEmailWithCapitalsAsTheOneAlreadyInUse() {
        given(userRepository.existsByEmail(EMAIL.toLowerCase())).willReturn(true);
        RegisterRequest request = validRegistration().email(EMAIL.toUpperCase()).build();

        assertThatThrownBy(() -> service.registerUser(request))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("should lock the account for 15 minutes on the third failed login (RN-03)")
    void shouldLockTheAccountFor15MinutesOnTheThirdFailedLogin() {
        User user = verifiedUser().failedLoginAttempts(2).build();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("incorrecta", "HASH")).willReturn(false);

        Instant before = Instant.now();
        assertThatThrownBy(() -> service.login(EMAIL, "incorrecta"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Muchos intentos seguidos. Por seguridad, pausamos el acceso por 15 minutos.");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(user.getAccountLockedUntil())
                .isBetween(before.plus(Duration.ofMinutes(15)), Instant.now().plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("should not lock the account before the third failed login (RN-03)")
    void shouldNotLockTheAccountBeforeTheThirdFailedLogin() {
        User user = verifiedUser().failedLoginAttempts(0).build();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("incorrecta", "HASH")).willReturn(false);

        assertThatThrownBy(() -> service.login(EMAIL, "incorrecta"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("El correo o la contraseña no coinciden. Revisa tus datos e inténtalo de nuevo.");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getAccountLockedUntil()).isNull();
    }

    @Test
    @DisplayName("should reject the login of a locked account even with the right password (RN-03)")
    void shouldRejectTheLoginOfALockedAccountEvenWithTheRightPassword() {
        User user =
                verifiedUser().accountLockedUntil(Instant.now().plus(Duration.ofMinutes(10))).build();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(VALID_PASSWORD, "HASH")).willReturn(true);

        assertThatThrownBy(() -> service.login(EMAIL, VALID_PASSWORD))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("El acceso sigue en pausa por seguridad. Puedes intentarlo en");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("should reject the login of an account whose email is not verified")
    void shouldRejectTheLoginOfAnAccountWhoseEmailIsNotVerified() {
        User user = verifiedUser().emailConfirmed(false).build();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(EMAIL, VALID_PASSWORD))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("ACCOUNT_NOT_VERIFIED");
    }

    @Test
    @DisplayName("should reset the failed attempts after a successful login")
    void shouldResetTheFailedAttemptsAfterASuccessfulLogin() {
        User user = verifiedUser().failedLoginAttempts(2).build();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(VALID_PASSWORD, "HASH")).willReturn(true);

        User logged = service.login(EMAIL, VALID_PASSWORD);

        assertThat(logged.getFailedLoginAttempts()).isZero();
        assertThat(logged.getLastLoginAt()).isNotNull();
    }

    // ---------------------------------------------------------------- [eliminar-cuenta]

    private static com.nexus.dto.DeleteAccountRequest deleteRequest(String password) {
        com.nexus.dto.DeleteAccountRequest r = new com.nexus.dto.DeleteAccountRequest();
        r.setPassword(password);
        return r;
    }

    @Test
    @DisplayName("[eliminar-cuenta] con calendario conectado y todo configurado, borra lo que bloqueaba antes de borrar al usuario")
    void deletesTheBlockingDataBeforeTheUser() {
        User ana = User.builder().id(4L).email(EMAIL).passwordHash("hash").build();
        given(userRepository.findById(4L)).willReturn(Optional.of(ana));
        given(passwordEncoder.matches("clave", "hash")).willReturn(true);
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.Query query = mock(jakarta.persistence.Query.class);
        given(em.createNativeQuery(any(String.class))).willReturn(query);
        given(query.setParameter(any(String.class), any())).willReturn(query);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "entityManager", em);

        service.deleteAccount(4L, deleteRequest("clave"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(em, org.mockito.Mockito.atLeastOnce()).createNativeQuery(sql.capture());
        assertThat(sql.getAllValues()).anySatisfy(s -> assertThat(s).startsWith("DELETE FROM calendars"));
        assertThat(sql.getAllValues()).anySatisfy(s -> assertThat(s).startsWith("DELETE FROM user_link_code_history"));
        assertThat(sql.getAllValues()).anySatisfy(s -> assertThat(s).startsWith("DELETE FROM date_ratings"));
        assertThat(sql.getAllValues()).anySatisfy(s -> assertThat(s).startsWith("DELETE FROM app_notifications"));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(query, userRepository);
        order.verify(query, org.mockito.Mockito.atLeastOnce()).executeUpdate();
        order.verify(userRepository).delete(ana);
    }

    @Test
    @DisplayName("[recuperar-sin-confirmar] should send a reset code even if the email is not confirmed yet")
    void shouldSendAResetCodeEvenIfTheEmailIsNotConfirmedYet() {
        User unverified = User.builder().id(4L).email(EMAIL).emailConfirmed(false)
                .accountStatus(AccountStatus.PENDING_VERIFICATION).build();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(unverified));
        given(verificationTokenRepository.save(any(VerificationToken.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.forgotPassword(EMAIL);

        ArgumentCaptor<VerificationToken> saved = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getPurpose()).isEqualTo(VerificationToken.TokenPurpose.PASSWORD_RESET);
    }

    @Test
    @DisplayName("[recuperar-sin-confirmar] resetting with the emailed code also confirms the email")
    void resettingWithTheEmailedCodeAlsoConfirmsTheEmail() {
        User unverified = User.builder().id(4L).email(EMAIL).emailConfirmed(false)
                .accountStatus(AccountStatus.PENDING_VERIFICATION).build();
        VerificationToken token = VerificationToken.builder().user(unverified).token("123456")
                .purpose(VerificationToken.TokenPurpose.PASSWORD_RESET)
                .expiresAt(Instant.now().plusSeconds(600)).build();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(unverified));
        given(verificationTokenRepository.findLatestValidToken(
                        org.mockito.ArgumentMatchers.eq(4L),
                        org.mockito.ArgumentMatchers.eq(VerificationToken.TokenPurpose.PASSWORD_RESET),
                        any(Instant.class)))
                .willReturn(Optional.of(token));
        given(passwordEncoder.encode(VALID_PASSWORD)).willReturn("NEW_HASH");

        service.resetPassword(EMAIL, "123456", VALID_PASSWORD, VALID_PASSWORD);

        assertThat(unverified.getEmailConfirmed()).isTrue();
        assertThat(unverified.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(unverified.getPasswordHash()).isEqualTo("NEW_HASH");
    }
}
