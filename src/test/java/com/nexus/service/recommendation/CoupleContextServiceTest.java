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
import com.nexus.service.EmotionService;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.anyLong;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** C3 - Las preferencias de los dos se suman por dimension; "Ninguna de estas" no cuenta. */
@ExtendWith(MockitoExtension.class)
class CoupleContextServiceTest {

    @Mock private UserPreferenceRepository preferences;
    @Mock private EmotionService emotions;
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

    @Test
    @DisplayName("C4: combina el animo de los dos en un solo nivel por eje, sin datos por persona")
    void combinesBothMoodsIntoOneLevel() {
        when(preferences.findByUserId(anyLong())).thenReturn(List.of());
        when(emotions.averageLastDays(1L, 7)).thenReturn(Optional.of(new EmotionService.MoodAverage(0.6, -0.5, 3)));
        when(emotions.averageLastDays(2L, 7)).thenReturn(Optional.of(new EmotionService.MoodAverage(0.4, -0.7, 2)));

        CoupleContext ctx = service.build(1L, link());

        assertThat(ctx.coupleValence()).isEqualTo(MoodLevel.ALTO);
        assertThat(ctx.coupleEnergy()).isEqualTo(MoodLevel.BAJO);
    }

    @Test
    @DisplayName("C4: con datos de uno solo usa ese; sin datos de nadie queda SIN_DATO")
    void usesWhoeverHasData() {
        when(preferences.findByUserId(anyLong())).thenReturn(List.of());
        when(emotions.averageLastDays(1L, 7)).thenReturn(Optional.of(new EmotionService.MoodAverage(0.1, 0.0, 1)));
        when(emotions.averageLastDays(2L, 7)).thenReturn(Optional.empty());

        CoupleContext ctx = service.build(1L, link());
        assertThat(ctx.coupleValence()).isEqualTo(MoodLevel.MEDIO);

        when(emotions.averageLastDays(1L, 7)).thenReturn(Optional.empty());
        assertThat(service.build(1L, link()).coupleEnergy()).isEqualTo(MoodLevel.SIN_DATO);
    }
}
