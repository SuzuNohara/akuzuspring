package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nexus.entity.Link;
import com.nexus.entity.Preference;
import com.nexus.entity.PreferenceCategory;
import com.nexus.entity.User;
import com.nexus.entity.UserPreference;
import com.nexus.model.IdeaCategory;
import com.nexus.repository.UserPreferenceRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** C3 - Las preferencias de los dos se suman por dimension; "Ninguna de estas" no cuenta. */
@ExtendWith(MockitoExtension.class)
class CoupleContextServiceTest {

    @Mock private UserPreferenceRepository preferences;
    @InjectMocks private CoupleContextService service;

    private static UserPreference pick(String category, String name) {
        PreferenceCategory cat = PreferenceCategory.builder().name(category).build();
        return UserPreference.builder().preference(Preference.builder().category(cat).name(name).build()).build();
    }

    private static Link link() {
        return Link.builder()
                .initiatorUser(User.builder().id(1L).build())
                .partnerUser(User.builder().id(2L).build())
                .build();
    }

    @Test
    @DisplayName("suma las preferencias de los dos en cada dimension")
    void sumsBothMembers() {
        when(preferences.findByUserId(1L))
                .thenReturn(List.of(pick("Bienestar Físico", "Yoga"), pick("Bienestar Social", "Cine")));
        when(preferences.findByUserId(2L))
                .thenReturn(List.of(pick("Bienestar Físico", "Ciclismo"), pick("Bienestar Ambiental", "Ninguna de estas")));

        CoupleContext ctx = service.build(2L, link());

        assertThat(ctx.weight(IdeaCategory.FISICO)).isEqualTo(2);
        assertThat(ctx.weight(IdeaCategory.SOCIAL)).isEqualTo(1);
        assertThat(ctx.weight(IdeaCategory.AMBIENTAL)).isZero();
        assertThat(ctx.weight(IdeaCategory.ESPIRITUAL)).isZero();
    }

    @Test
    @DisplayName("una categoria con nombre desconocido se ignora")
    void ignoresUnknownCategory() {
        when(preferences.findByUserId(1L)).thenReturn(List.of(pick("Otra cosa", "X")));
        when(preferences.findByUserId(2L)).thenReturn(List.of());

        assertThat(service.build(1L, link()).dimensionWeights()).isEmpty();
    }
}
