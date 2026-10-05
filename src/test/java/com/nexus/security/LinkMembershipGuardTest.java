package com.nexus.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nexus.entity.Link;
import com.nexus.exception.ForbiddenException;
import com.nexus.repository.LinkRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A diferencia de {@link OwnershipGuard} (pura, sin dependencias), esta guarda consulta el
 * vinculo activo en base de datos: cubre recursos que el dueno Y su pareja vinculada pueden ver
 * (p. ej. el avatar de perfil, {@code GET /profile/{userId}/avatar}).
 */
class LinkMembershipGuardTest {

    private static final AuthenticatedUser USER_7 = new AuthenticatedUser(7L, "a@b.co");

    private final LinkRepository linkRepository = mock(LinkRepository.class);
    private final LinkMembershipGuard guard = new LinkMembershipGuard(linkRepository);

    @Test
    @DisplayName("should not throw and not query the repository when the user requests their own resource")
    void shouldNotThrowAndNotQueryTheRepositoryWhenTheUserRequestsTheirOwnResource() {
        assertThatCode(() -> guard.requireSelfOrLinkedPartner(USER_7, 7L)).doesNotThrowAnyException();
        verifyNoInteractions(linkRepository);
    }

    @Test
    @DisplayName("should not throw when the user has an active link with the resource owner")
    void shouldNotThrowWhenTheUserHasAnActiveLinkWithTheResourceOwner() {
        given(linkRepository.findActiveLinkBetweenUsers(7L, 9L))
                .willReturn(Optional.of(mock(Link.class)));

        assertThatCode(() -> guard.requireSelfOrLinkedPartner(USER_7, 9L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should throw forbidden when there is no active link with the resource owner")
    void shouldThrowForbiddenWhenThereIsNoActiveLinkWithTheResourceOwner() {
        given(linkRepository.findActiveLinkBetweenUsers(7L, 9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireSelfOrLinkedPartner(USER_7, 9L))
                .isInstanceOf(ForbiddenException.class);
    }
}
