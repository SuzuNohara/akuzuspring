package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nexus.dto.UserPreferenceRequest;
import com.nexus.entity.User;
import com.nexus.entity.UserPreference;
import com.nexus.exception.BadRequestException;
import com.nexus.repository.PreferenceCategoryRepository;
import com.nexus.repository.PreferenceRepository;
import com.nexus.repository.UserPreferenceRepository;
import com.nexus.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-12/RF-13: solo se guardan preferencias que pertenecen a la taxonomia. */
class PreferenceServiceTest {

    private static final long USER_ID = 4L;
    private static final long UNKNOWN_PREFERENCE_ID = 999L;

    private final PreferenceRepository preferenceRepository = mock(PreferenceRepository.class);
    private final UserPreferenceRepository userPreferenceRepository =
            mock(UserPreferenceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private final PreferenceService service =
            new PreferenceService(
                    mock(PreferenceCategoryRepository.class),
                    preferenceRepository,
                    userPreferenceRepository,
                    userRepository);

    @Test
    @DisplayName("should reject as a bad request a preference that is not in the taxonomy")
    void shouldRejectAsABadRequestAPreferenceThatIsNotInTheTaxonomy() {
        given(userRepository.existsById(USER_ID)).willReturn(true);
        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(User.builder().id(USER_ID).build()));
        given(preferenceRepository.findById(UNKNOWN_PREFERENCE_ID)).willReturn(Optional.empty());
        UserPreferenceRequest request = new UserPreferenceRequest();
        request.setPreferenceId(UNKNOWN_PREFERENCE_ID);
        request.setLevel(80);

        assertThatThrownBy(() -> service.saveUserPreferences(USER_ID, List.of(request)))
                .isInstanceOf(BadRequestException.class);
        verify(userPreferenceRepository, never()).save(any(UserPreference.class));
    }
}
