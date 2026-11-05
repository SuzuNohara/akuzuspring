package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nexus.dto.OnboardingProgressResponse;
import com.nexus.entity.Link;
import com.nexus.entity.User;
import com.nexus.repository.DateRatingRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.repository.RecommendationRequestRepository;
import com.nexus.repository.UserPreferenceRepository;
import com.nexus.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [primeros-pasos] B-01..B-11: avance de los hitos de la primera configuracion. */
class OnboardingServiceTest {

    private static final long ANA = 1L;
    private static final long SAM = 2L;
    private static final long LINK_ID = 7L;
    private static final Instant LINKED_AT = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant CUSTOMIZED_AT = Instant.parse("2026-10-02T09:30:00Z");

    private final UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
    private final LinkRepository links = mock(LinkRepository.class);
    private final ExternalCalendarRepository calendars = mock(ExternalCalendarRepository.class);
    private final RecommendationRequestRepository requests = mock(RecommendationRequestRepository.class);
    private final DateRatingRepository ratings = mock(DateRatingRepository.class);
    private final UserRepository users = mock(UserRepository.class);

    private final OnboardingService service =
            new OnboardingService(preferences, links, calendars, requests, ratings, users);

    private final User ana = User.builder().id(ANA).displayName("Ana").build();
    private final User sam = User.builder().id(SAM).displayName("Sam").build();
    private final Link link = Link.builder().id(LINK_ID).startedAt(LINKED_AT)
            .initiatorUser(ana)
            .partnerUser(sam)
            .build();

    @BeforeEach
    void knownUsers() {
        given(users.findById(ANA)).willReturn(Optional.of(ana));
        given(users.findById(SAM)).willReturn(Optional.of(sam));
    }

    private void linked() {
        given(links.findActiveLinkByUserId(ANA)).willReturn(Optional.of(link));
    }

    private void unlinked() {
        given(links.findActiveLinkByUserId(ANA)).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("B-01 cuenta nueva: todo en false y sin pareja")
    void newAccountHasNothing() {
        unlinked();

        OnboardingProgressResponse p = service.progressFor(ANA);

        assertThat(p.me()).isEqualTo(new OnboardingProgressResponse.Mine(false, false, false, false));
        assertThat(p.partner()).isNull();
        assertThat(p.couple()).isEqualTo(new OnboardingProgressResponse.Couple(false, false));
    }

    @Test
    @DisplayName("B-02 con preferencias guardadas el cuestionario esta hecho")
    void questionnaireDoneWithPreferences() {
        given(preferences.existsByUserId(ANA)).willReturn(true);

        assertThat(service.progressFor(ANA).me().questionnaire()).isTrue();
    }

    @Test
    @DisplayName("B-03 un calendario activo cuenta; si no hay activos, no (R-06: solo existencia)")
    void activeCalendarCounts() {
        given(calendars.existsActiveByUserId(ANA)).willReturn(true);
        assertThat(service.progressFor(ANA).me().calendar()).isTrue();

        given(calendars.existsActiveByUserId(ANA)).willReturn(false);
        assertThat(service.progressFor(ANA).me().calendar()).isFalse();

        verify(calendars, never()).findByUserIdAndIsActiveTrue(anyLong());
    }

    @Test
    @DisplayName("B-04 R-03: sin schedule_customized_at los horarios de fabrica no cuentan")
    void defaultScheduleDoesNotCount() {
        unlinked();

        assertThat(service.progressFor(ANA).me().schedule()).isFalse();
    }

    @Test
    @DisplayName("B-04b R-03: con schedule_customized_at los horarios cuentan")
    void customizedScheduleCounts() {
        unlinked();
        ana.setScheduleCustomizedAt(CUSTOMIZED_AT);

        assertThat(service.progressFor(ANA).me().schedule()).isTrue();
    }

    @Test
    @DisplayName("B-05 con vinculo trae a la pareja con su nombre y sus tres hitos")
    void linkedBringsPartner() {
        linked();
        given(preferences.existsByUserId(SAM)).willReturn(true);
        given(calendars.existsActiveByUserId(SAM)).willReturn(true);
        sam.setScheduleCustomizedAt(CUSTOMIZED_AT);

        OnboardingProgressResponse p = service.progressFor(ANA);

        assertThat(p.me().linked()).isTrue();
        assertThat(p.me().schedule()).isFalse();
        assertThat(p.partner()).isEqualTo(new OnboardingProgressResponse.Partner("Sam", true, true, true));
    }

    @Test
    @DisplayName("B-05b quien fue invitado ve como pareja a quien invito")
    void partnerFromTheOtherSide() {
        given(links.findActiveLinkByUserId(SAM)).willReturn(Optional.of(link));
        ana.setScheduleCustomizedAt(CUSTOMIZED_AT);

        OnboardingProgressResponse p = service.progressFor(SAM);

        assertThat(p.partner().name()).isEqualTo("Ana");
        assertThat(p.partner().schedule()).isTrue();
        assertThat(p.me().schedule()).isFalse();
    }

    @Test
    @DisplayName("B-05c R-05: con vinculo los dos User salen del Link, sin users.findById")
    void linkedDoesNotReloadUsers() {
        linked();

        service.progressFor(ANA);

        verify(users, never()).findById(any());
    }

    @Test
    @DisplayName("B-05d R-05: sin vinculo se carga el User una sola vez")
    void unlinkedLoadsUserOnce() {
        unlinked();

        service.progressFor(ANA);

        verify(users, times(1)).findById(ANA);
    }

    @Test
    @DisplayName("B-06 sin vinculo los hitos de pareja quedan en false aunque haya calificaciones")
    void noLinkNoCoupleMilestones() {
        unlinked();
        given(ratings.existsForLink(anyLong())).willReturn(true);
        given(requests.existsByLink_Id(anyLong())).willReturn(true);

        assertThat(service.progressFor(ANA).couple())
                .isEqualTo(new OnboardingProgressResponse.Couple(false, false));
    }

    @Test
    @DisplayName("B-07 una recomendacion pedida en este vinculo cuenta")
    void recommendationOfThisLink() {
        linked();
        given(requests.existsByLink_Id(LINK_ID)).willReturn(true);

        assertThat(service.progressFor(ANA).couple().firstRecommendation()).isTrue();
    }

    @Test
    @DisplayName("B-08 R-04: cuenta una calificacion de una cita de este vinculo")
    void ratingOfThisLinkCounts() {
        linked();
        given(ratings.existsForLink(LINK_ID)).willReturn(true);

        assertThat(service.progressFor(ANA).couple().firstRatedDate()).isTrue();
    }

    @Test
    @DisplayName("B-09 R-04: sin calificaciones de citas de este vinculo, no cuenta")
    void noRatingOfThisLink() {
        linked();
        given(ratings.existsForLink(LINK_ID)).willReturn(false);

        assertThat(service.progressFor(ANA).couple().firstRatedDate()).isFalse();
    }

    @Test
    @DisplayName("B-10 R-08: linked y partner != null dicen siempre lo mismo")
    void linkedMatchesPartnerPresence() {
        unlinked();
        OnboardingProgressResponse alone = service.progressFor(ANA);
        assertThat(alone.me().linked()).isEqualTo(alone.partner() != null).isFalse();

        linked();
        OnboardingProgressResponse together = service.progressFor(ANA);
        assertThat(together.me().linked()).isEqualTo(together.partner() != null).isTrue();
    }
}
