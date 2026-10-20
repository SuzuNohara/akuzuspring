package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nexus.dto.PendingRatingResponse;
import com.nexus.dto.RatingResponse;
import com.nexus.entity.DateRating;
import com.nexus.entity.Event;
import com.nexus.entity.EventStatus;
import com.nexus.entity.Link;
import com.nexus.entity.User;
import com.nexus.exception.BadRequestException;
import com.nexus.exception.ConflictException;
import com.nexus.exception.ForbiddenException;
import com.nexus.exception.ResourceNotFoundException;
import com.nexus.repository.DateRatingRepository;
import com.nexus.repository.EventRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.security.AesEncryptionService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** CU42 / RF-41 - Calificar una cita: ventana de 48 h (RN-36), una vez, privada y cifrada (RN-37, RNF-11). */
class RatingServiceTest {

    private static final long ANA = 1L;
    private static final long SAM = 2L;
    private static final Instant NOW = Instant.parse("2026-10-19T18:00:00Z");

    private final LinkRepository links = mock(LinkRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final DateRatingRepository ratings = mock(DateRatingRepository.class);
    private final AesEncryptionService aes = mock(AesEncryptionService.class);

    private final RatingService service =
            new RatingService(links, events, ratings, aes, Clock.fixed(NOW, ZoneOffset.UTC));

    private final Link link = Link.builder().id(7L)
            .initiatorUser(User.builder().id(ANA).build())
            .partnerUser(User.builder().id(SAM).build())
            .build();

    /** Cita confirmada que termino hace {@code endedAgo}. */
    private Event date(long id, String title, Duration endedAgo) {
        Instant end = NOW.minus(endedAgo);
        return Event.builder().id(id).title(title).link(link)
                .startDateTime(end.minus(Duration.ofHours(2))).endDateTime(end)
                .location("Coyoacán").status(EventStatus.CONFIRMED)
                .creatorApproved(true).partnerApproved(true).isRecurring(false)
                .build();
    }

    @BeforeEach
    void setUp() {
        given(links.findActiveLinkByUserId(ANA)).willReturn(Optional.of(link));
        given(ratings.findRatedEventIds(eq(ANA), anyCollection())).willReturn(List.of());
    }

    @Test
    @DisplayName("should list finished dates within 48 hours, the closest to expire first")
    void shouldListFinishedDatesWithin48HoursClosestToExpireFirst() {
        Event recent = date(1, "Cine al aire libre", Duration.ofHours(17));
        Event older = date(2, "Cena a cuatro manos", Duration.ofHours(43));
        Event expired = date(3, "Paseo", Duration.ofHours(49));
        Event upcoming = date(4, "Concierto", Duration.ofHours(-5));
        given(events.findByLinkIdAndStatus(7L, EventStatus.CONFIRMED))
                .willReturn(List.of(recent, older, expired, upcoming));

        List<PendingRatingResponse> pending = service.pending(ANA);

        assertThat(pending).extracting(PendingRatingResponse::eventId).containsExactly(2L, 1L);
        assertThat(pending.get(0).expiresAt()).isEqualTo(older.getEndDateTime().plus(Duration.ofHours(48)));
    }

    @Test
    @DisplayName("should leave out dates already rated, recurring events and deleted ones")
    void shouldLeaveOutRatedRecurringAndDeleted() {
        Event rated = date(1, "Cine", Duration.ofHours(2));
        Event recurring = date(2, "Clase semanal", Duration.ofHours(2));
        recurring.setIsRecurring(true);
        Event deleted = date(3, "Borrado", Duration.ofHours(2));
        deleted.setDeletedAt(NOW.minus(Duration.ofHours(1)));
        Event open = date(4, "Cena", Duration.ofHours(2));
        given(events.findByLinkIdAndStatus(7L, EventStatus.CONFIRMED)).willReturn(List.of(rated, recurring, deleted, open));
        given(ratings.findRatedEventIds(eq(ANA), anyCollection())).willReturn(List.of(1L));

        assertThat(service.pending(ANA)).extracting(PendingRatingResponse::eventId).containsExactly(4L);
    }

    @Test
    @DisplayName("should have nothing to rate without an active link")
    void shouldHaveNothingToRateWithoutAnActiveLink() {
        given(links.findActiveLinkByUserId(ANA)).willReturn(Optional.empty());

        assertThat(service.pending(ANA)).isEmpty();
    }

    @Test
    @DisplayName("should save the score encrypted and never in clear")
    void shouldSaveTheScoreEncrypted() {
        given(events.findByIdAndUserId(1L, ANA)).willReturn(Optional.of(date(1, "Cine", Duration.ofHours(3))));
        given(aes.encrypt(anyString())).willReturn("CIFRADO");
        given(ratings.save(any(DateRating.class))).willAnswer(inv -> inv.getArgument(0));

        RatingResponse response = service.rate(ANA, 1L, 4);

        ArgumentCaptor<String> plain = ArgumentCaptor.forClass(String.class);
        verify(aes).encrypt(plain.capture());
        assertThat(plain.getValue()).contains("4");
        ArgumentCaptor<DateRating> saved = ArgumentCaptor.forClass(DateRating.class);
        verify(ratings).save(saved.capture());
        assertThat(saved.getValue().getEncryptedScore()).isEqualTo("CIFRADO");
        assertThat(saved.getValue().getUserId()).isEqualTo(ANA);
        assertThat(saved.getValue().getEventId()).isEqualTo(1L);
        assertThat(response.eventId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("FA01: should refuse a rating after 48 hours")
    void shouldRefuseARatingAfter48Hours() {
        given(events.findByIdAndUserId(1L, ANA)).willReturn(Optional.of(date(1, "Cine", Duration.ofHours(49))));

        assertThatThrownBy(() -> service.rate(ANA, 1L, 4))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ya no es posible calificar");
        verify(ratings, never()).save(any());
    }

    @Test
    @DisplayName("should refuse a date that has not finished")
    void shouldRefuseADateThatHasNotFinished() {
        given(events.findByIdAndUserId(1L, ANA)).willReturn(Optional.of(date(1, "Cine", Duration.ofHours(-1))));

        assertThatThrownBy(() -> service.rate(ANA, 1L, 4)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("FA02: should refuse a second rating of the same date")
    void shouldRefuseASecondRating() {
        given(events.findByIdAndUserId(1L, ANA)).willReturn(Optional.of(date(1, "Cine", Duration.ofHours(3))));
        given(ratings.existsByEventIdAndUserId(1L, ANA)).willReturn(true);

        assertThatThrownBy(() -> service.rate(ANA, 1L, 4)).isInstanceOf(ConflictException.class);
        verify(ratings, never()).save(any());
    }

    @Test
    @DisplayName("should refuse a score outside 1 to 5")
    void shouldRefuseAScoreOutside1To5() {
        assertThatThrownBy(() -> service.rate(ANA, 1L, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.rate(ANA, 1L, 6)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("should not reveal dates of other people")
    void shouldNotRevealDatesOfOtherPeople() {
        given(events.findByIdAndUserId(9L, ANA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.rate(ANA, 9L, 4)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("should refuse a pending, rejected or recurring event")
    void shouldRefuseAnEventThatIsNotADate() {
        Event pending = date(1, "Cine", Duration.ofHours(3));
        pending.setStatus(EventStatus.PENDING);
        given(events.findByIdAndUserId(1L, ANA)).willReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.rate(ANA, 1L, 4)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("should refuse a date of a previous link")
    void shouldRefuseADateOfAPreviousLink() {
        Link old = Link.builder().id(3L).initiatorUser(User.builder().id(ANA).build())
                .partnerUser(User.builder().id(5L).build()).build();
        Event oldDate = date(1, "Cine", Duration.ofHours(3));
        oldDate.setLink(old);
        given(events.findByIdAndUserId(1L, ANA)).willReturn(Optional.of(oldDate));

        assertThatThrownBy(() -> service.rate(ANA, 1L, 4)).isInstanceOf(ForbiddenException.class);
    }
}
