package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nexus.dto.IdeaResponse;
import com.nexus.entity.Activity;
import com.nexus.exception.ForbiddenException;
import com.nexus.model.IdeaCategory;
import com.nexus.repository.ActivityRepository;
import com.nexus.repository.LinkRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RF-34 (banco de ideas por categoria) y RN-29 (solo con vinculo activo). La pertenencia del
 * {@code {userId}} la impone {@code IdeaController}; aqui solo el vinculo y el mapeo.
 */
class IdeaServiceTest {

    private static final long USER_ID = 4L;

    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final LinkRepository linkRepository = mock(LinkRepository.class);
    private final IdeaService service = new IdeaService(activityRepository, linkRepository);

    private static Activity activity(long id, String title, String type, String scope) {
        return Activity.builder()
                .id(id)
                .title(title)
                .description("Descripcion de " + title)
                .activityType(type)
                .locationScope(scope)
                .durationAvg(120)
                .costMxnPp(0)
                .priceBand("FREE")
                .outdoor(false)
                .build();
    }

    @Test
    @DisplayName("should reject the idea bank without an active link (RN-29) and not read the catalog")
    void shouldRejectTheIdeaBankWithoutAnActiveLinkAndNotReadTheCatalog() {
        given(linkRepository.existsActiveLinkByUserId(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> service.listIdeas(USER_ID, Optional.empty()))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(activityRepository);
    }

    @Test
    @DisplayName("should list every idea, mapped to its wellness dimension, when no category is given")
    void shouldListEveryIdeaMappedToItsWellnessDimensionWhenNoCategoryIsGiven() {
        given(linkRepository.existsActiveLinkByUserId(USER_ID)).willReturn(true);
        given(activityRepository.findAllByOrderByTitleAsc())
                .willReturn(
                        List.of(
                                activity(1, "Cena en casa", "DINNER", "HOME"),
                                activity(2, "Cine al aire libre", "FILM", "CITY")));

        List<IdeaResponse> ideas = service.listIdeas(USER_ID, Optional.empty());

        assertThat(ideas).extracting(IdeaResponse::getTitle).containsExactly("Cena en casa", "Cine al aire libre");
        assertThat(ideas.get(0).getCategory()).isEqualTo("EMOCIONAL");
        assertThat(ideas.get(0).getCategoryLabel()).isEqualTo("Emocional");
        assertThat(ideas.get(0).isAtHome()).isTrue();
        assertThat(ideas.get(1).isAtHome()).isFalse();
    }

    @Test
    @DisplayName("should only query the activity types of the requested category")
    void shouldOnlyQueryTheActivityTypesOfTheRequestedCategory() {
        given(linkRepository.existsActiveLinkByUserId(USER_ID)).willReturn(true);
        given(
                        activityRepository.findByActivityTypeInOrderByTitleAsc(
                                IdeaCategory.INTELECTUAL.activityTypes()))
                .willReturn(List.of(activity(2, "Museo", "EXHIBITION_VISIT", "CITY")));

        List<IdeaResponse> ideas = service.listIdeas(USER_ID, Optional.of(IdeaCategory.INTELECTUAL));

        assertThat(ideas).singleElement().satisfies(i -> assertThat(i.getCategory()).isEqualTo("INTELECTUAL"));
    }

    @Test
    @DisplayName("should put OTHER and unknown activity types in SOCIAL when listing SOCIAL")
    void shouldPutOtherAndUnknownActivityTypesInSocialWhenListingSocial() {
        given(linkRepository.existsActiveLinkByUserId(USER_ID)).willReturn(true);
        given(activityRepository.findAllByOrderByTitleAsc())
                .willReturn(
                        List.of(
                                activity(1, "Karaoke", "KARAOKE_NUEVO", "CITY"),
                                activity(2, "Karaoke en casa", "OTHER", "HOME"),
                                activity(3, "Cena", "DINNER", "HOME")));

        List<IdeaResponse> ideas = service.listIdeas(USER_ID, Optional.of(IdeaCategory.SOCIAL));

        assertThat(ideas).extracting(IdeaResponse::getTitle).containsExactly("Karaoke", "Karaoke en casa");
    }

    @Test
    @DisplayName("should list the categories in display order with their labels")
    void shouldListTheCategoriesInDisplayOrderWithTheirLabels() {
        assertThat(service.listCategories())
                .extracting(c -> c.getCode())
                .containsExactly(
                        "FISICO", "EMOCIONAL", "SOCIAL", "INTELECTUAL", "PROFESIONAL", "AMBIENTAL",
                        "ESPIRITUAL");
        assertThat(service.listCategories().get(0).getLabel()).isEqualTo("Físico");
    }
}
