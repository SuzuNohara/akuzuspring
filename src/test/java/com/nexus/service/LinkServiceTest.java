package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nexus.dto.LinkCodeResponse;
import com.nexus.dto.LinkStatusResponse;
import com.nexus.entity.Link;
import com.nexus.entity.LinkCode;
import com.nexus.entity.User;
import com.nexus.exception.BadRequestException;
import com.nexus.repository.EventRepository;
import com.nexus.repository.LinkCodeRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Reglas de la vinculacion de pareja: RN-08 (exclusividad), RN-09 (vigencia) y RN-10. */
class LinkServiceTest {

    private static final long GENERATOR_ID = 4L;
    private static final long PARTNER_ID = 9L;
    private static final String CODE = "AB7K-M9P2";

    private final LinkRepository linkRepository = mock(LinkRepository.class);
    private final LinkCodeRepository linkCodeRepository = mock(LinkCodeRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private final LinkService service =
            new LinkService(
                    linkRepository,
                    linkCodeRepository,
                    userRepository,
                    mock(NotificationService.class),
                    mock(EventRepository.class));

    private final User generator = User.builder().id(GENERATOR_ID).displayName("Ana").build();
    private final User partner = User.builder().id(PARTNER_ID).displayName("Luis").build();

    private LinkCode codeFromGenerator(Instant expiresAt, boolean used) {
        return LinkCode.builder()
                .code(CODE)
                .generatedByUser(generator)
                .isUsed(used)
                .expiresAt(expiresAt)
                .build();
    }

    private void givenBothUsersExist() {
        given(userRepository.findById(GENERATOR_ID)).willReturn(Optional.of(generator));
        given(userRepository.findById(PARTNER_ID)).willReturn(Optional.of(partner));
    }

    @Test
    @DisplayName("should not generate a code for a user who already has an active link (RN-08)")
    void shouldNotGenerateACodeForAUserWhoAlreadyHasAnActiveLink() {
        givenBothUsersExist();
        given(linkRepository.existsActiveLinkByUserId(GENERATOR_ID)).willReturn(true);

        assertThatThrownBy(() -> service.generateLinkCode(GENERATOR_ID))
                .isInstanceOf(BadRequestException.class);
        verify(linkCodeRepository, never()).save(any(LinkCode.class));
    }

    @Test
    @DisplayName("should generate a code that expires in 15 minutes (RN-09)")
    void shouldGenerateACodeThatExpiresIn15Minutes() {
        givenBothUsersExist();
        given(linkCodeRepository.findActiveCodeByUserId(GENERATOR_ID)).willReturn(Optional.empty());

        Instant before = Instant.now();
        LinkCodeResponse response = service.generateLinkCode(GENERATOR_ID);
        Instant after = Instant.now();

        assertThat(response.getExpiresAt())
                .isBetween(before.plus(Duration.ofMinutes(15)), after.plus(Duration.ofMinutes(15)));
        verify(linkCodeRepository).save(any(LinkCode.class));
    }

    @Test
    @DisplayName("should return the code still in force instead of generating another one")
    void shouldReturnTheCodeStillInForceInsteadOfGeneratingAnotherOne() {
        givenBothUsersExist();
        given(linkCodeRepository.findActiveCodeByUserId(GENERATOR_ID))
                .willReturn(Optional.of(codeFromGenerator(Instant.now().plusSeconds(300), false)));

        LinkCodeResponse response = service.generateLinkCode(GENERATOR_ID);

        assertThat(response.getCode()).isEqualTo(CODE);
        verify(linkCodeRepository, never()).save(any(LinkCode.class));
    }

    @Test
    @DisplayName("should reject an expired code (RN-09)")
    void shouldRejectAnExpiredCode() {
        givenBothUsersExist();
        given(linkCodeRepository.findByCode(CODE))
                .willReturn(Optional.of(codeFromGenerator(Instant.now().minusSeconds(60), false)));

        assertThatThrownBy(() -> service.establishLink(PARTNER_ID, CODE))
                .isInstanceOf(BadRequestException.class);
        verify(linkRepository, never()).save(any(Link.class));
    }

    @Test
    @DisplayName("should reject a code that was already used")
    void shouldRejectACodeThatWasAlreadyUsed() {
        givenBothUsersExist();
        given(linkCodeRepository.findByCode(CODE))
                .willReturn(Optional.of(codeFromGenerator(Instant.now().plusSeconds(300), true)));

        assertThatThrownBy(() -> service.establishLink(PARTNER_ID, CODE))
                .isInstanceOf(BadRequestException.class);
        verify(linkRepository, never()).save(any(Link.class));
    }

    @Test
    @DisplayName("should reject linking with your own code (RN-10)")
    void shouldRejectLinkingWithYourOwnCode() {
        givenBothUsersExist();
        given(linkCodeRepository.findByCode(CODE))
                .willReturn(Optional.of(codeFromGenerator(Instant.now().plusSeconds(300), false)));

        assertThatThrownBy(() -> service.establishLink(GENERATOR_ID, CODE))
                .isInstanceOf(BadRequestException.class);
        verify(linkRepository, never()).save(any(Link.class));
    }

    @Test
    @DisplayName("should reject the link when the code owner already has an active link (RN-08)")
    void shouldRejectTheLinkWhenTheCodeOwnerAlreadyHasAnActiveLink() {
        givenBothUsersExist();
        given(linkCodeRepository.findByCode(CODE))
                .willReturn(Optional.of(codeFromGenerator(Instant.now().plusSeconds(300), false)));
        given(linkRepository.existsActiveLinkByUserId(GENERATOR_ID)).willReturn(true);

        assertThatThrownBy(() -> service.establishLink(PARTNER_ID, CODE))
                .isInstanceOf(BadRequestException.class);
        verify(linkRepository, never()).save(any(Link.class));
    }

    @Test
    @DisplayName("should create an active link and mark the code as used")
    void shouldCreateAnActiveLinkAndMarkTheCodeAsUsed() {
        givenBothUsersExist();
        LinkCode code = codeFromGenerator(Instant.now().plusSeconds(300), false);
        given(linkCodeRepository.findByCode(CODE)).willReturn(Optional.of(code));

        LinkStatusResponse response = service.establishLink(PARTNER_ID, CODE);

        ArgumentCaptor<Link> saved = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository).save(saved.capture());
        assertThat(saved.getValue().getIsActive()).isTrue();
        assertThat(saved.getValue().getInitiatorUser()).isSameAs(generator);
        assertThat(saved.getValue().getPartnerUser()).isSameAs(partner);
        assertThat(code.getIsUsed()).isTrue();
        assertThat(response.isHasActiveLink()).isTrue();
    }
}
