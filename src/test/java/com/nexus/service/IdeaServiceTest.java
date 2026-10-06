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
    @DisplayName("should list every idea, mapped to its friendly category, when no category is given")
    void shouldListEveryIdeaMappedToItsFriendlyCategoryWhenNoCategoryIsGiven() {
        given(linkRepository.existsActiveLinkByUserId(USER_ID)).willReturn(true);
        given(activityRepository.findAllByOrderByTitleAsc())
                .willReturn(
                        List.of(
                                activity(1, "Cena en casa", "DINNER", "HOME"),
                                activity(2, "Cine al aire libre", "FILM", "CITY")));

        List<IdeaResponse> ideas = service.listIdeas(USER_ID, Optional.empty());

        assertThat(ideas).extracting(IdeaResponse::getTitle).containsExactly("Cena en casa", "Cine al aire libre");
        assertThat(ideas.get(0).getCategory()).isEqualTo("COMER_Y_BEBER");
        assertThat(ideas.get(0).getCategoryLabel()).isEqualTo("Comer y beber");
        assertThat(ideas.get(0).isAtHome()).isTrue();
        assertThat(ideas.get(1).isAtHome()).isFalse();
    }

    @Test
    @DisplayName("should only query the activity types of the requested category")
    void shouldOnlyQueryTheActivityTypesOfTheRequestedCategory() {
        given(linkRepository.existsActiveLinkByUserId(USER_ID)).willReturn(true);
        given(
                        activityRepository.findByActivityTypeInOrderByTitleAsc(
                                IdeaCategory.CULTURA.activityTypes()))
                .willReturn(List.of(activity(2, "Concierto", "CONCERT", "CITY")));

        List<IdeaResponse> ideas = service.listIdeas(USER_ID, Optional.of(IdeaCategory.CULTURA));

        assertThat(ideas).singleElement().satisfies(i -> assertThat(i.getCategory()).isEqualTo("CULTURA"));
    }

    @Test
    @DisplayName("should put unknown activity types in OTRAS when listing OTRAS")
    void shouldPutUnknownActivityTypesInOtrasWhenListingOtras() {
        given(linkRepository.existsActiveLinkByUserId(USER_ID)).willReturn(true);
        given(activityRepository.findAllByOrderByTitleAsc())
                .willReturn(
                        List.of(
                                activity(1, "Karaoke", "KARAOKE_NUEVO", "CITY"),
                                activity(2, "Compras", "SHOPPING", "CITY"),
                                activity(3, "Cena", "DINNER", "HOME")));

        List<IdeaResponse> ideas = service.listIdeas(USER_ID, Optional.of(IdeaCategory.OTRAS));

        assertThat(ideas).extracting(IdeaResponse::getTitle).containsExactly("Karaoke", "Compras");
    }

    @Test
    @DisplayName("should list the categories in display order with their labels")
    void shouldListTheCategoriesInDisplayOrderWithTheirLabels() {
        assertThat(service.listCategories())
                .extracting(c -> c.getCode())
                .containsExactly(
                        "COMER_Y_BEBER", "CULTURA", "AIRE_LIBRE", "DEPORTE", "APRENDER",
                        "JUEGOS_Y_RELAX", "OTRAS");
        assertThat(service.listCategories().get(0).getLabel()).isEqualTo("Comer y beber");
    }
}
