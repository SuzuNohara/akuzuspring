package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.nexus.dto.OnboardingProgressResponse;
import com.nexus.entity.AvailabilitySchedule;
import com.nexus.entity.ExternalCalendar;
import com.nexus.entity.Link;
import com.nexus.entity.User;
import com.nexus.repository.AvailabilityScheduleRepository;
import com.nexus.repository.DateRatingRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.repository.RecommendationRequestRepository;
import com.nexus.repository.UserPreferenceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [primeros-pasos] B-01..B-09: avance de los hitos de la primera configuracion. */
class OnboardingServiceTest {

    private static final long ANA = 1L;
    private static final long SAM = 2L;
    private static final long LINK_ID = 7L;
    private static final Instant LINKED_AT = Instant.parse("2026-10-01T12:00:00Z");

    private final UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
    private final LinkRepository links = mock(LinkRepository.class);
    private final ExternalCalendarRepository calendars = mock(ExternalCalendarRepository.class);
    private final AvailabilityScheduleRepository schedules = mock(AvailabilityScheduleRepository.class);
    private final RecommendationRequestRepository requests = mock(RecommendationRequestRepository.class);
    private final DateRatingRepository ratings = mock(DateRatingRepository.class);

    private final OnboardingService service =
            new OnboardingService(preferences, links, calendars, schedules, requests, ratings);

    private final Link link = Link.builder().id(LINK_ID).startedAt(LINKED_AT)
            .initiatorUser(User.builder().id(ANA).displayName("Ana").build())
            .partnerUser(User.builder().id(SAM).displayName("Sam").build())
            .build();

    private void linked() {
        given(links.findActiveLinkByUserId(ANA)).willReturn(Optional.of(link));
    }

    @Test
    @DisplayName("B-01 cuenta nueva: todo en false y sin pareja")
    void newAccountHasNothing() {
        given(links.findActiveLinkByUserId(ANA)).willReturn(Optional.empty());

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
    @DisplayName("B-03 un calendario activo cuenta; si no hay activos, no")
    void activeCalendarCounts() {
        given(calendars.findByUserIdAndIsActiveTrue(ANA)).willReturn(List.of(new ExternalCalendar()));
        assertThat(service.progressFor(ANA).me().calendar()).isTrue();

        given(calendars.findByUserIdAndIsActiveTrue(ANA)).willReturn(List.of());
        assertThat(service.progressFor(ANA).me().calendar()).isFalse();
    }

    @Test
    @DisplayName("B-04 con al menos un dia de horario guardado")
    void savedScheduleCounts() {
        given(schedules.findByUserId(ANA)).willReturn(List.of(new AvailabilitySchedule()));

        assertThat(service.progressFor(ANA).me().schedule()).isTrue();
    }

    @Test
    @DisplayName("B-05 con vinculo trae a la pareja con su nombre y sus tres hitos")
    void linkedBringsPartner() {
        linked();
        given(preferences.existsByUserId(SAM)).willReturn(true);
        given(calendars.findByUserIdAndIsActiveTrue(SAM)).willReturn(List.of(new ExternalCalendar()));

        OnboardingProgressResponse p = service.progressFor(ANA);

        assertThat(p.me().linked()).isTrue();
        assertThat(p.partner()).isEqualTo(new OnboardingProgressResponse.Partner("Sam", true, true, false));
    }

    @Test
    @DisplayName("B-05b quien fue invitado ve como pareja a quien invito")
    void partnerFromTheOtherSide() {
        given(links.findActiveLinkByUserId(SAM)).willReturn(Optional.of(link));

        assertThat(service.progressFor(SAM).partner().name()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("B-06 sin vinculo los hitos de pareja quedan en false aunque haya calificaciones")
    void noLinkNoCoupleMilestones() {
        given(links.findActiveLinkByUserId(ANA)).willReturn(Optional.empty());
        given(ratings.existsByUserIdInAndCreatedAtGreaterThanEqual(anyCollection(), any())).willReturn(true);
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
    @DisplayName("B-08 basta con que califique cualquiera de los dos")
    void eitherRatingCounts() {
        linked();
        given(ratings.existsByUserIdInAndCreatedAtGreaterThanEqual(
                argThat(ids -> ids.containsAll(List.of(ANA, SAM)) && ids.size() == 2), eq(LINKED_AT)))
                .willReturn(true);

        assertThat(service.progressFor(ANA).couple().firstRatedDate()).isTrue();
    }

    @Test
    @DisplayName("B-09 las calificaciones de antes del vinculo actual no cuentan")
    void ratingsBeforeThisLinkDoNotCount() {
        linked();
        given(ratings.existsByUserIdInAndCreatedAtGreaterThanEqual(anyCollection(), eq(LINKED_AT)))
                .willReturn(false);

        assertThat(service.progressFor(ANA).couple().firstRatedDate()).isFalse();
    }
}
