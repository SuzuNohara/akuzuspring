package com.nexus.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OwnershipGuardTest {

    private static final AuthenticatedUser USER_7 = new AuthenticatedUser(7L, "a@b.co");

    @Test
    @DisplayName("requireSelf should not throw when the authenticated user matches the path id")
    void requireSelfShouldNotThrowWhenTheAuthenticatedUserMatchesThePathId() {
        assertThatCode(() -> OwnershipGuard.requireSelf(USER_7, 7L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireSelf should throw forbidden when the authenticated user does not match the path id")
    void requireSelfShouldThrowForbiddenWhenTheAuthenticatedUserDoesNotMatchThePathId() {
        assertThatThrownBy(() -> OwnershipGuard.requireSelf(USER_7, 9L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("requireParticipant should not throw when the authenticated user is either id")
    void requireParticipantShouldNotThrowWhenTheAuthenticatedUserIsEitherId() {
        assertThatCode(() -> OwnershipGuard.requireParticipant(USER_7, 7L, 20L))
                .doesNotThrowAnyException();
        assertThatCode(() -> OwnershipGuard.requireParticipant(USER_7, 20L, 7L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireParticipant should throw forbidden when the authenticated user is neither id")
    void requireParticipantShouldThrowForbiddenWhenTheAuthenticatedUserIsNeitherId() {
        assertThatThrownBy(() -> OwnershipGuard.requireParticipant(USER_7, 20L, 21L))
                .isInstanceOf(ForbiddenException.class);
    }
}
