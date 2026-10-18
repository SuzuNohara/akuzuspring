package com.nexus.service.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexus.dto.CreateEventResponse;
import com.nexus.dto.EventResponse;
import com.nexus.dto.RecommendationBatch;
import com.nexus.dto.planning.PlanningFilters;
import com.nexus.dto.planning.PlanningView;
import com.nexus.dto.planning.ReadyRequest;
import com.nexus.entity.Activity;
import com.nexus.entity.Link;
import com.nexus.entity.PlanningSession;
import com.nexus.entity.PlanningVote;
import com.nexus.entity.Recommendation;
import com.nexus.entity.RecommendationRequest;
import com.nexus.entity.User;
import com.nexus.exception.BadRequestException;
import com.nexus.exception.ConflictException;
import com.nexus.exception.ForbiddenException;
import com.nexus.model.PlanningStatus;
import com.nexus.model.ReasonSource;
import com.nexus.model.RequestStatus;
import com.nexus.model.Scope;
import com.nexus.model.Vote;
import com.nexus.model.ZoneType;
import com.nexus.repository.BoroughRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.repository.PlanningMessageRepository;
import com.nexus.repository.PlanningSessionRepository;
import com.nexus.repository.PlanningVoteRepository;
import com.nexus.repository.RecommendationRepository;
import com.nexus.repository.RecommendationRequestRepository;
import com.nexus.repository.UserRepository;
import com.nexus.service.AppNotificationService;
import com.nexus.service.EventService;
import com.nexus.service.recommendation.CoupleCriteria;
import com.nexus.service.recommendation.RecommendationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** RF-38 / RF-39 - Planeacion en pareja (B1-B8) con repositorios simulados. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanningServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T18:00:00Z");
    private static final long ANA = 4L;
    private static final long SAM = 13L;
    private static final long LINK = 7L;
    private static final long SESSION = 50L;
    private static final long REQUEST = 90L;

    @Mock private PlanningSessionRepository sessions;
    @Mock private PlanningVoteRepository votes;
    @Mock private PlanningMessageRepository messages;
    @Mock private LinkRepository links;
    @Mock private UserRepository users;
    @Mock private BoroughRepository boroughs;
    @Mock private RecommendationRepository recommendations;
    @Mock private RecommendationRequestRepository requests;
    @Mock private RecommendationService recommendationService;
    @Mock private EventService events;
    @Mock private AppNotificationService notifications;

    private PlanningService service;
    private final Map<PlanningVote.Key, PlanningVote> voteStore = new HashMap<>();
    private final List<Recommendation> stored = new ArrayList<>();
    private Link link;

    @BeforeEach
    void setUp() {
        service = new PlanningService(sessions, votes, messages, links, users, boroughs, recommendations, requests,
                recommendationService, events, notifications, new SessionLocations(), Clock.fixed(NOW, ZoneOffset.UTC));
        User ana = User.builder().id(ANA).displayName("Ana").build();
        User sam = User.builder().id(SAM).displayName("Sam").build();
        link = Link.builder().id(LINK).initiatorUser(ana).partnerUser(sam).build();
        when(users.findById(ANA)).thenReturn(Optional.of(ana));
        when(users.findById(SAM)).thenReturn(Optional.of(sam));
        when(links.findActiveLinkByUserId(anyLong())).thenReturn(Optional.of(link));
        when(links.findById(LINK)).thenReturn(Optional.of(link));
        when(sessions.save(any())).thenAnswer(i -> {
            PlanningSession s = i.getArgument(0);
            if (s.getId() == null) s.setId(SESSION);
            return s;
        });
        when(votes.save(any())).thenAnswer(i -> {
            PlanningVote v = i.getArgument(0);
            voteStore.put(new PlanningVote.Key(v.getRecommendationId(), v.getUserId()), v);
            return v;
        });
        when(votes.findById(any())).thenAnswer(i -> Optional.ofNullable(voteStore.get(i.getArgument(0))));
        when(votes.findBySessionId(SESSION)).thenAnswer(i -> new ArrayList<>(voteStore.values()));
        when(recommendations.findByRequestIdOrderByPositionAsc(REQUEST)).thenAnswer(i -> stored);
    }

    private PlanningSession session(PlanningStatus status) {
        PlanningSession s = PlanningSession.builder().id(SESSION).linkId(LINK).inviterId(ANA).partnerId(SAM)
                .status(status).inviteExpiresAt(NOW.plus(Duration.ofHours(20))).build();
        if (status == PlanningStatus.ACTIVE) {
            s.setAcceptedAt(NOW.minus(Duration.ofHours(1)));
            s.setSessionExpiresAt(NOW.plus(Duration.ofHours(11)));
            s.setScope(Scope.ANY);
            s.setMaxCostMxn(1500);
        }
        when(sessions.findById(SESSION)).thenReturn(Optional.of(s));
        return s;
    }

    private PlanningSession withOptions(int count) {
        PlanningSession s = session(PlanningStatus.ACTIVE);
        s.setZoneType(ZoneType.NONE);
        s.setSlotStart(NOW.plus(Duration.ofDays(1)));
        s.setSlotEnd(NOW.plus(Duration.ofDays(1)).plus(Duration.ofHours(3)));
        s.setRequestId(REQUEST);
        RecommendationRequest request = RecommendationRequest.builder().id(REQUEST).slotStart(s.getSlotStart())
                .slotEnd(s.getSlotEnd()).status(RequestStatus.OPEN).build();
        for (int i = 1; i <= count; i++) {
            Activity a = Activity.builder().id((long) i).title("Plan " + i).activityType("FILM").locationScope("HOME")
                    .durationMin(30).durationAvg(60).costMxnPp(0).build();
            stored.add(Recommendation.builder().id(100L + i).request(request).activity(a).round(1).position(i)
                    .reason("frase").reasonSource(ReasonSource.CATALOG).shown(i <= 5).build());
        }
        stored.forEach(r -> when(recommendations.findById(r.getId())).thenReturn(Optional.of(r)));
        return s;
    }

    // --- B1 ---------------------------------------------------------------------------------

    @Test
    @DisplayName("invitar crea la planeacion con 24 h y le avisa a la pareja")
    void invite() {
        PlanningView view = service.invite(ANA);

        assertThat(view.phase()).isEqualTo("INVITED");
        assertThat(view.role()).isEqualTo("INVITER");
        assertThat(view.inviteExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        verify(notifications).notify(eq(SAM), eq(SESSION), eq("INVITE"), eq("Ana te invita a planear una cita"), any());
    }

    @Test
    @DisplayName("no se puede invitar si ya hay una planeacion abierta")
    void onlyOneOpen() {
        PlanningSession open = session(PlanningStatus.INVITED);
        when(sessions.findFirstByLinkIdAndStatusInOrderByIdDesc(eq(LINK), anyList())).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.invite(ANA)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("solo la pareja se une; al unirse empiezan las 12 h")
    void accept() {
        session(PlanningStatus.INVITED);
        assertThatThrownBy(() -> service.accept(ANA, SESSION)).isInstanceOf(ForbiddenException.class);

        PlanningView view = service.accept(SAM, SESSION);

        assertThat(view.phase()).isEqualTo("FILTERS");
        assertThat(view.sessionExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
        verify(notifications).notify(eq(ANA), eq(SESSION), eq("JOINED"), eq("Sam se unió a la planeación"), any());
    }

    @Test
    @DisplayName("la invitacion vence a las 24 h y se avisa a los dos (B8a)")
    void inviteExpires() {
        PlanningSession s = session(PlanningStatus.INVITED);
        s.setInviteExpiresAt(NOW.minusSeconds(1));
        when(sessions.findFirstByLinkIdOrderByIdDesc(LINK)).thenReturn(Optional.of(s));

        PlanningView view = service.current(ANA).orElseThrow();

        assertThat(view.status()).isEqualTo("EXPIRED");
        verify(notifications).notify(eq(ANA), eq(SESSION), eq("EXPIRED"), eq("Sam no aceptó a tiempo"), any());
        verify(notifications).notify(eq(SAM), eq(SESSION), eq("EXPIRED"), eq("La invitación de Ana venció"), any());
    }

    @Test
    @DisplayName("a las 12 h sin acuerdo la sesion se cierra y se borran los mensajes (B8b)")
    void sessionCloses() {
        PlanningSession s = session(PlanningStatus.ACTIVE);
        s.setSessionExpiresAt(NOW.minusSeconds(1));
        when(sessions.findByStatusIn(anyList())).thenReturn(List.of(s));

        service.sweep();

        assertThat(s.getStatus()).isEqualTo(PlanningStatus.CLOSED);
        verify(messages).deleteBySessionId(SESSION);
    }

    @Test
    @DisplayName("el barrido manda el recordatorio de la invitacion una sola vez")
    void reminderOnce() {
        PlanningSession s = session(PlanningStatus.INVITED);
        s.setInviteExpiresAt(NOW.plus(Duration.ofHours(11)));
        when(sessions.findByStatusIn(anyList())).thenReturn(List.of(s));

        service.sweep();
        service.sweep();

        verify(notifications).notify(eq(SAM), eq(SESSION), eq("REMINDER"), eq("Recordatorio"),
                eq("La invitación de Ana vence en 11 horas."));
    }

    @Test
    @DisplayName("un recordatorio que sale tarde dice el tiempo que de verdad queda")
    void lateReminderSaysRealTime() {
        PlanningSession s = session(PlanningStatus.ACTIVE);
        s.setSessionExpiresAt(NOW.plus(Duration.ofMinutes(230)));
        when(sessions.findByStatusIn(anyList())).thenReturn(List.of(s));

        service.sweep();

        verify(notifications).notify(eq(ANA), eq(SESSION), eq("REMINDER"), eq("Quedan 3 h 50 min"), any());
        verify(notifications).notify(eq(SAM), eq(SESSION), eq("REMINDER"), eq("Quedan 3 h 50 min"), any());
    }

    // --- cierre con opcion pendiente ---------------------------------------------------------

    private PlanningSession closedWithProposal() {
        PlanningSession s = withOptions(5);
        s.setProposalRecommendationId(102L);
        s.setProposedById(ANA);
        s.setSessionExpiresAt(NOW.minusSeconds(1));
        when(sessions.findFirstByLinkIdOrderByIdDesc(LINK)).thenReturn(Optional.of(s));
        return s;
    }

    private void like(long recommendationId, long userId) {
        voteStore.put(new PlanningVote.Key(recommendationId, userId), PlanningVote.builder()
                .recommendationId(recommendationId).userId(userId).sessionId(SESSION).vote(Vote.LIKE).build());
    }

    @Test
    @DisplayName("si se acaba el tiempo con una propuesta, se dice quien no respondio y la opcion se conserva")
    void closedKeepsProposal() {
        closedWithProposal();

        PlanningView view = service.current(ANA).orElseThrow();

        assertThat(view.phase()).isEqualTo("CLOSED");
        assertThat(view.leftover().option().item().title()).isEqualTo("Plan 2");
        assertThat(view.leftover().kind()).isEqualTo("MY_PROPOSAL");
        verify(notifications).notify(eq(ANA), eq(SESSION), eq("CLOSED"), eq("Se acabó el tiempo"),
                eq("Sam no alcanzó a responder tu propuesta."));
        verify(notifications).notify(eq(SAM), eq(SESSION), eq("CLOSED"), eq("Se acabó el tiempo"),
                eq("No alcanzaste a responder la propuesta de Ana."));
    }

    @Test
    @DisplayName("si coincidieron sin proponer, la opcion en la que coincidieron se conserva")
    void closedKeepsMatch() {
        PlanningSession s = withOptions(5);
        like(103L, ANA);
        like(103L, SAM);
        s.setSessionExpiresAt(NOW.minusSeconds(1));
        when(sessions.findFirstByLinkIdOrderByIdDesc(LINK)).thenReturn(Optional.of(s));

        PlanningView view = service.current(SAM).orElseThrow();

        assertThat(view.leftover().option().item().title()).isEqualTo("Plan 3");
        assertThat(view.leftover().kind()).isEqualTo("MATCH");
        verify(notifications).notify(eq(SAM), eq(SESSION), eq("CLOSED"), eq("Se acabó el tiempo"),
                eq("Coincidieron en \"Plan 3\", pero nadie la propuso."));
    }

    @Test
    @DisplayName("sin coincidencias se cierra como antes y no queda opcion")
    void closedWithoutMatch() {
        PlanningSession s = withOptions(5);
        s.setSessionExpiresAt(NOW.minusSeconds(1));
        when(sessions.findFirstByLinkIdOrderByIdDesc(LINK)).thenReturn(Optional.of(s));

        PlanningView view = service.current(ANA).orElseThrow();

        assertThat(view.leftover()).isNull();
        verify(notifications).notify(eq(ANA), eq(SESSION), eq("CLOSED"), eq("Se acabó el tiempo"),
                eq("No llegaron a un acuerdo a tiempo."));
    }

    @Test
    @DisplayName("al terminar, la opcion que quedo se puede mandar como invitacion")
    void inviteLeftover() {
        closedWithProposal();
        EventResponse event = new EventResponse();
        event.setId(77L);
        CreateEventResponse created = new CreateEventResponse();
        created.setEvent(event);
        when(events.createEvent(eq(ANA), any())).thenReturn(created);

        PlanningView view = service.inviteLeftover(ANA, SESSION);

        assertThat(view.eventId()).isEqualTo(77L);
        assertThat(view.leftover()).isNull();
        assertThat(stored.get(1).getRequest().getStatus()).isEqualTo(RequestStatus.SENT);
        assertThatThrownBy(() -> service.inviteLeftover(ANA, SESSION)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("si el horario de la opcion ya paso, no se puede mandar")
    void leftoverInThePast() {
        PlanningSession s = closedWithProposal();
        stored.get(1).getRequest().setSlotStart(NOW.minus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> service.inviteLeftover(ANA, SESSION)).isInstanceOf(BadRequestException.class);
        verify(events, never()).createEvent(anyLong(), any());
        assertThat(s.getEventId()).isNull();
    }

    // --- B2 ---------------------------------------------------------------------------------

    @Test
    @DisplayName("cambiar filtros quita el listo de los dos y le avisa a la pareja que cambio")
    void filtersChange() {
        PlanningSession s = session(PlanningStatus.ACTIVE);
        s.setInviterReady(true);

        service.updateFilters(SAM, SESSION, new PlanningFilters(ZoneType.NONE, null, Scope.ANY, 3000, List.of(), null, null));

        assertThat(s.getInviterReady()).isFalse();
        verify(notifications).notifyGrouped(eq(ANA), eq(SESSION), eq("FILTERS"), eq("Sam cambió los filtros"),
                eq("Sam eligió \"Sin preferencia\""), any());
    }

    @Test
    @DisplayName("varias dimensiones: agregar una se describe por su nombre")
    void addCategory() {
        PlanningSession s = session(PlanningStatus.ACTIVE);
        s.setZoneType(ZoneType.NONE);

        service.updateFilters(SAM, SESSION,
                new PlanningFilters(ZoneType.NONE, null, Scope.ANY, 1500, List.of("SOCIAL", "EMOCIONAL"), null, null));

        assertThat(s.getCategories()).isEqualTo("EMOCIONAL,SOCIAL");
        verify(notifications).notifyGrouped(eq(ANA), eq(SESSION), eq("FILTERS"), any(),
                startsWith("Sam agregó el plan"), any());
    }

    @Test
    @DisplayName("Listo sin horario no se acepta; con Cerca de nosotros exige la ubicacion")
    void readyValidation() {
        PlanningSession s = session(PlanningStatus.ACTIVE);
        s.setZoneType(ZoneType.NEAR_US);
        assertThatThrownBy(() -> service.ready(ANA, SESSION, null)).isInstanceOf(BadRequestException.class);

        s.setSlotStart(NOW.plus(Duration.ofDays(1)));
        s.setSlotEnd(NOW.plus(Duration.ofDays(1)).plus(Duration.ofHours(2)));
        assertThatThrownBy(() -> service.ready(ANA, SESSION, new ReadyRequest(null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ubicación");
    }

    @Test
    @DisplayName("cuando los dos estan listos se buscan las opciones con las dos ubicaciones")
    void bothReadySearch() {
        PlanningSession s = session(PlanningStatus.ACTIVE);
        s.setZoneType(ZoneType.NEAR_US);
        s.setSlotStart(NOW.plus(Duration.ofDays(1)));
        s.setSlotEnd(NOW.plus(Duration.ofDays(1)).plus(Duration.ofHours(2)));
        when(recommendationService.requestForCouple(eq(ANA), eq(link), any()))
                .thenReturn(new RecommendationBatch(REQUEST, List.of(), Set.of("BUDGET"), "CATALOG", true));

        PlanningView first = service.ready(ANA, SESSION, new ReadyRequest(19.4, -99.1));
        assertThat(first.phase()).isEqualTo("FILTERS");
        verify(notifications).notify(eq(SAM), eq(SESSION), eq("READY"), any(), any());

        PlanningView second = service.ready(SAM, SESSION, new ReadyRequest(19.3, -99.2));

        ArgumentCaptor<CoupleCriteria> criteria = ArgumentCaptor.forClass(CoupleCriteria.class);
        verify(recommendationService).requestForCouple(eq(ANA), eq(link), criteria.capture());
        assertThat(criteria.getValue().references()).hasSize(2);
        assertThat(s.getRequestId()).isEqualTo(REQUEST);
        assertThat(second.phase()).isEqualTo("OPTIONS");
        assertThat(second.relaxedFilters()).containsExactly("BUDGET");
        verify(notifications).notify(eq(ANA), eq(SESSION), eq("OPTIONS"), eq("Ya tienen opciones"), any());
    }

    // --- B3 ---------------------------------------------------------------------------------

    @Test
    @DisplayName("si a los dos no les convence, entra la siguiente")
    void bothDislike() {
        withOptions(7);

        service.vote(ANA, SESSION, 101L, Vote.DISLIKE);
        PlanningView view = service.vote(SAM, SESSION, 101L, Vote.DISLIKE);

        assertThat(view.options()).extracting(o -> o.item().title())
                .containsExactly("Plan 2", "Plan 3", "Plan 4", "Plan 5", "Plan 6");
        assertThat(view.hasMore()).isTrue();
    }

    @Test
    @DisplayName("si a los dos les gusta, coinciden y se avisa a quien voto primero")
    void bothLike() {
        withOptions(5);

        service.vote(ANA, SESSION, 101L, Vote.LIKE);
        PlanningView view = service.vote(SAM, SESSION, 101L, Vote.LIKE);

        assertThat(view.options().get(0).myVote()).isEqualTo("LIKE");
        assertThat(view.options().get(0).partnerVote()).isEqualTo("LIKE");
        verify(notifications).notify(eq(ANA), eq(SESSION), eq("MATCH"), eq("¡Coinciden!"), any());
    }

    @Test
    @DisplayName("buscar mas muestra las siguientes; agotadas pide ajustar filtros (B7)")
    void moreAndExhausted() {
        withOptions(7);

        PlanningView view = service.more(ANA, SESSION);
        assertThat(view.options()).extracting(o -> o.item().title()).containsExactly("Plan 6", "Plan 7");
        assertThat(view.hasMore()).isFalse();

        assertThatThrownBy(() -> service.more(ANA, SESSION)).isInstanceOf(ConflictException.class);

        PlanningView adjusting = service.adjust(ANA, SESSION);
        assertThat(adjusting.phase()).isEqualTo("FILTERS");
        assertThat(adjusting.adjustingByPartner()).isFalse();
    }

    // --- B4, B5 -----------------------------------------------------------------------------

    @Test
    @DisplayName("una propuesta a la vez; la pareja la acepta y el evento queda aprobado por los dos")
    void proposeAndAccept() {
        PlanningSession s = withOptions(5);
        EventResponse event = new EventResponse();
        event.setId(300L);
        CreateEventResponse created = new CreateEventResponse();
        created.setEvent(event);
        when(events.createEvent(eq(ANA), any())).thenReturn(created);

        PlanningView proposed = service.propose(ANA, SESSION, 102L);
        assertThat(proposed.phase()).isEqualTo("PROPOSAL");
        assertThat(proposed.proposal().byMe()).isTrue();
        assertThatThrownBy(() -> service.propose(SAM, SESSION, 103L)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.acceptProposal(ANA, SESSION)).isInstanceOf(ForbiddenException.class);

        PlanningView agreed = service.acceptProposal(SAM, SESSION);

        assertThat(agreed.status()).isEqualTo("AGREED");
        assertThat(agreed.agreed().item().title()).isEqualTo("Plan 2");
        assertThat(s.getEventId()).isEqualTo(300L);
        verify(events).approveEvent(SAM, 300L);
        verify(messages).deleteBySessionId(SESSION);
        verify(notifications).notify(eq(ANA), eq(SESSION), eq("AGREED"), eq("¡Cita confirmada!"), any());
    }

    @Test
    @DisplayName("seguir buscando quita la propuesta y vuelven a las opciones")
    void rejectProposal() {
        withOptions(5);
        service.propose(ANA, SESSION, 102L);

        PlanningView view = service.rejectProposal(SAM, SESSION);

        assertThat(view.phase()).isEqualTo("OPTIONS");
        verify(events, never()).createEvent(anyLong(), any());
    }

    // --- B6 ---------------------------------------------------------------------------------

    @Test
    @DisplayName("20 mensajes por hora por persona: el 21 se rechaza")
    void messageLimit() {
        session(PlanningStatus.ACTIVE);
        when(messages.countBySessionIdAndSenderIdAndCreatedAtAfter(eq(SESSION), eq(ANA), any())).thenReturn(20L);

        assertThatThrownBy(() -> service.sendMessage(ANA, SESSION, "hola")).isInstanceOf(ConflictException.class);
        verify(messages, never()).save(any());
    }

    @Test
    @DisplayName("un mensaje se guarda y avisa a la pareja agrupando los nuevos")
    void messageSent() {
        session(PlanningStatus.ACTIVE);
        when(messages.countBySessionIdAndSenderIdAndCreatedAtAfter(eq(SESSION), eq(ANA), any())).thenReturn(0L, 2L);

        service.sendMessage(ANA, SESSION, "  ¿Y si mejor el cine?  ");

        verify(messages).save(any());
        verify(notifications).notifyGrouped(eq(SAM), eq(SESSION), eq("MESSAGES"), eq("Mensajes nuevos de Ana"),
                eq("2 mensajes en su planeación."), any());
    }
}
