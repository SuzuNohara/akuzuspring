package com.nexus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nexus.dto.CreateEventRequest;
import com.nexus.dto.EventResponse;
import com.nexus.dto.UpdateEventRequest;
import com.nexus.entity.Event;
import com.nexus.entity.EventStatus;
import com.nexus.entity.Link;
import com.nexus.entity.User;
import com.nexus.exception.BadRequestException;
import com.nexus.exception.ForbiddenException;
import com.nexus.repository.EventExceptionRepository;
import com.nexus.repository.EventRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Reglas de la agenda compartida: RN-15 (vinculo activo), RN-16 y RN-17 (aprobacion mutua). */
class EventServiceTest {

    private static final long CREATOR_ID = 4L;
    private static final long PARTNER_ID = 9L;
    private static final long EVENT_ID = 21L;

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final LinkRepository linkRepository = mock(LinkRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private final EventService service =
            new EventService(
                    eventRepository,
                    linkRepository,
                    userRepository,
                    mock(NotificationService.class),
                    mock(EventExceptionRepository.class));

    private final User creator = User.builder().id(CREATOR_ID).displayName("Ana").build();
    private final User partner = User.builder().id(PARTNER_ID).displayName("Luis").build();
    private final Link link =
            Link.builder().id(1L).initiatorUser(creator).partnerUser(partner).isActive(true).build();

    private static CreateEventRequest tomorrowForTwoHours() {
        Instant start = Instant.now().plus(Duration.ofDays(1));
        return CreateEventRequest.builder()
                .title("Cena")
                .startDateTime(start.toString())
                .endDateTime(start.plus(Duration.ofHours(2)).toString())
                .build();
    }

    private Event.EventBuilder event() {
        return Event.builder()
                .id(EVENT_ID)
                .title("Cena")
                .creator(creator)
                .link(link)
                .isRecurring(false)
                .reminders(new ArrayList<>());
    }

    private void givenSaveReturnsTheSameEvent() {
        given(eventRepository.save(any(Event.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("should not create an event for a user without an active link (RN-15)")
    void shouldNotCreateAnEventForAUserWithoutAnActiveLink() {
        given(userRepository.findActiveById(CREATOR_ID)).willReturn(Optional.of(creator));
        given(linkRepository.findActiveLinkByUserId(CREATOR_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createEvent(CREATOR_ID, tomorrowForTwoHours()))
                .isInstanceOf(BadRequestException.class);
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("should create the event as pending, approved only by its creator (RN-16)")
    void shouldCreateTheEventAsPendingApprovedOnlyByItsCreator() {
        given(userRepository.findActiveById(CREATOR_ID)).willReturn(Optional.of(creator));
        given(linkRepository.findActiveLinkByUserId(CREATOR_ID)).willReturn(Optional.of(link));
        givenSaveReturnsTheSameEvent();

        service.createEvent(CREATOR_ID, tomorrowForTwoHours());

        ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(EventStatus.PENDING);
        assertThat(saved.getValue().getCreatorApproved()).isTrue();
        assertThat(saved.getValue().getPartnerApproved()).isFalse();
    }

    @Test
    @DisplayName("should reject an event that starts in the past")
    void shouldRejectAnEventThatStartsInThePast() {
        given(userRepository.findActiveById(CREATOR_ID)).willReturn(Optional.of(creator));
        given(linkRepository.findActiveLinkByUserId(CREATOR_ID)).willReturn(Optional.of(link));
        Instant start = Instant.now().minus(Duration.ofHours(3));
        CreateEventRequest request =
                CreateEventRequest.builder()
                        .title("Cena")
                        .startDateTime(start.toString())
                        .endDateTime(start.plus(Duration.ofHours(1)).toString())
                        .build();

        assertThatThrownBy(() -> service.createEvent(CREATOR_ID, request))
                .isInstanceOf(BadRequestException.class);
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("should not let the creator approve their own event (RN-16)")
    void shouldNotLetTheCreatorApproveTheirOwnEvent() {
        Event pending =
                event().status(EventStatus.PENDING).creatorApproved(true).partnerApproved(false).build();
        given(eventRepository.findByIdAndUserId(EVENT_ID, CREATOR_ID)).willReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.approveEvent(CREATOR_ID, EVENT_ID))
                .isInstanceOf(BadRequestException.class);
        assertThat(pending.getStatus()).isEqualTo(EventStatus.PENDING);
    }

    @Test
    @DisplayName("should confirm the event when the partner approves it (RN-16)")
    void shouldConfirmTheEventWhenThePartnerApprovesIt() {
        Event pending =
                event().status(EventStatus.PENDING).creatorApproved(true).partnerApproved(false).build();
        given(eventRepository.findByIdAndUserId(EVENT_ID, PARTNER_ID)).willReturn(Optional.of(pending));
        givenSaveReturnsTheSameEvent();

        EventResponse response = service.approveEvent(PARTNER_ID, EVENT_ID);

        assertThat(response.getStatus()).isEqualTo(EventStatus.CONFIRMED);
        assertThat(pending.getPartnerApproved()).isTrue();
    }

    @Test
    @DisplayName("should send a confirmed event back to pending when its date changes (RN-17)")
    void shouldSendAConfirmedEventBackToPendingWhenItsDateChanges() {
        Event confirmed =
                event().status(EventStatus.CONFIRMED).creatorApproved(true).partnerApproved(true).build();
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(confirmed));
        given(userRepository.findById(CREATOR_ID)).willReturn(Optional.of(creator));
        givenSaveReturnsTheSameEvent();
        UpdateEventRequest request = new UpdateEventRequest();
        request.setStartDateTime(Instant.now().plus(Duration.ofDays(2)).toString());

        service.updateEvent(EVENT_ID, CREATOR_ID, request);

        assertThat(confirmed.getStatus()).isEqualTo(EventStatus.PENDING);
        assertThat(confirmed.getCreatorApproved()).isTrue();
        assertThat(confirmed.getPartnerApproved()).isFalse();
    }

    @Test
    @DisplayName("should forbid editing an event to someone outside its link")
    void shouldForbidEditingAnEventToSomeoneOutsideItsLink() {
        long strangerId = 30L;
        Event confirmed =
                event().status(EventStatus.CONFIRMED).creatorApproved(true).partnerApproved(true).build();
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(confirmed));
        given(userRepository.findById(strangerId))
                .willReturn(Optional.of(User.builder().id(strangerId).build()));
        UpdateEventRequest request = new UpdateEventRequest();
        request.setTitle("Otro titulo");

        assertThatThrownBy(() -> service.updateEvent(EVENT_ID, strangerId, request))
                .isInstanceOf(ForbiddenException.class);
        verify(eventRepository, never()).save(any(Event.class));
        assertThat(confirmed.getTitle()).isEqualTo("Cena");
    }

    @Test
    @DisplayName("should keep the event confirmed when only its description changes (RN-17)")
    void shouldKeepTheEventConfirmedWhenOnlyItsDescriptionChanges() {
        Event confirmed =
                event().status(EventStatus.CONFIRMED).creatorApproved(true).partnerApproved(true).build();
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(confirmed));
        given(userRepository.findById(CREATOR_ID)).willReturn(Optional.of(creator));
        givenSaveReturnsTheSameEvent();
        UpdateEventRequest request = new UpdateEventRequest();
        request.setDescription("Llevar vino");

        service.updateEvent(EVENT_ID, CREATOR_ID, request);

        assertThat(confirmed.getStatus()).isEqualTo(EventStatus.CONFIRMED);
        assertThat(confirmed.getPartnerApproved()).isTrue();
    }
}
