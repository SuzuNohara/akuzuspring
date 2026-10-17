package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexus.dto.CreateEventRequest;
import com.nexus.dto.CreateEventResponse;
import com.nexus.dto.DiscardResponse;
import com.nexus.dto.RecommendationBatch;
import com.nexus.dto.RecommendationParams;
import com.nexus.entity.Activity;
import com.nexus.entity.Borough;
import com.nexus.entity.Link;
import com.nexus.entity.Place;
import com.nexus.entity.Recommendation;
import com.nexus.entity.RecommendationRequest;
import com.nexus.entity.User;
import com.nexus.exception.BadRequestException;
import com.nexus.exception.ConflictException;
import com.nexus.exception.ForbiddenException;
import com.nexus.model.ReasonSource;
import com.nexus.model.RequestStatus;
import com.nexus.model.Scope;
import com.nexus.model.ZoneType;
import com.nexus.repository.BoroughRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.repository.PlaceRepository;
import com.nexus.repository.RecommendationRepository;
import com.nexus.repository.RecommendationRequestRepository;
import com.nexus.repository.UserRepository;
import com.nexus.service.EventService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** U9 - Pedir, buscar mas, descartar e invitar en la modalidad individual (A1-A18). */
class RecommendationServiceTest {

    private static final long ANA = 1L;
    private static final long SAM = 2L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);
    // Sabado 17-oct 19:00-21:00 en CDMX
    private static final Instant START = Instant.parse("2026-10-18T01:00:00Z");
    private static final Instant END = Instant.parse("2026-10-18T03:00:00Z");

    private final LinkRepository links = mock(LinkRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BoroughRepository boroughs = mock(BoroughRepository.class);
    private final PlaceRepository places = mock(PlaceRepository.class);
    private final RecommendationRequestRepository requests = mock(RecommendationRequestRepository.class);
    private final RecommendationRepository recommendations = mock(RecommendationRepository.class);
    private final CandidateSource candidates = mock(CandidateSource.class);
    private final ReasonWriter writer = mock(ReasonWriter.class);
    private final CoupleContextService contexts = mock(CoupleContextService.class);
    private final MutualSlotChecker slots = mock(MutualSlotChecker.class);
    private final EventService events = mock(EventService.class);

    private final RecommendationService service = new RecommendationService(
            links, users, boroughs, places, requests, recommendations, candidates, writer, contexts, slots, events, CLOCK);

    private final User ana = User.builder().id(ANA).build();
    private final Link link = Link.builder().id(7L).initiatorUser(ana).partnerUser(User.builder().id(SAM).build()).build();

    private static Activity activity(long id, String title, String type, String scope, int avg) {
        return Activity.builder().id(id).title(title).activityType(type).locationScope(scope)
                .durationMin(30).durationAvg(avg).costMxnPp(150).build();
    }

    private static RecommendationParams params(ZoneType zone, String borough, Double lat, Double lon, Instant start, Instant end) {
        return new RecommendationParams(zone, borough, lat, lon, Scope.ANY, 1500, null, start, end);
    }

    private static RecommendationParams simple() {
        return params(ZoneType.NONE, null, null, null, START, END);
    }

    @BeforeEach
    void setUp() {
        when(links.findActiveLinkByUserId(ANA)).thenReturn(Optional.of(link));
        when(slots.isMutualFree(ANA, SAM, START, END)).thenReturn(true);
        when(contexts.build(eq(ANA), any())).thenReturn(CoupleContext.empty());
        when(users.getReferenceById(ANA)).thenReturn(ana);
    }

    @Test
    @DisplayName("A20: las alcaldias salen en orden, solo con codigo y nombre")
    void listsBoroughs() {
        when(boroughs.findAllByOrderByNameAsc()).thenReturn(List.of(
                Borough.builder().code("ALVARO_OBREGON").name("Álvaro Obregón").lat(19.36).lon(-99.2).build(),
                Borough.builder().code("COYOACAN").name("Coyoacán").lat(19.35).lon(-99.16).build()));

        assertThat(service.boroughs()).extracting(b -> b.code()).containsExactly("ALVARO_OBREGON", "COYOACAN");
    }

    @Nested
    class Request {

        private final List<Recommendation> saved = new ArrayList<>();

        @BeforeEach
        void stubSelection() {
            List<Candidate> picked = new ArrayList<>();
            for (long i = 1; i <= 7; i++) {
                picked.add(new Candidate(activity(i, "Plan " + i, "BOARD_GAME", "HOME", 60), null, 0));
            }
            picked.add(new Candidate(activity(8, "Cine", "FILM", "CITY", 120), new PlaceChoice(30L, "Cineteca", "Coyoacán", 2.0), 0));
            when(candidates.select(any(), any(), eq(Set.of()), eq(RecommendationService.STORED)))
                    .thenReturn(new Selection(picked, Set.of(RelaxedFilter.CATEGORY), SelectionSource.CATALOG));
            when(writer.write(anyList(), any())).thenAnswer(inv -> ((List<Candidate>) inv.getArgument(0)).stream()
                    .map(c -> new WrittenReason(c.activity().getId(), "frase " + c.activity().getId(), ReasonSource.CATALOG))
                    .toList());
            when(places.getReferenceById(30L)).thenReturn(Place.builder().id(30L).name("Cineteca").borough("Coyoacán").build());
            when(requests.save(any())).thenAnswer(inv -> {
                RecommendationRequest r = inv.getArgument(0);
                r.setId(100L);
                return r;
            });
            AtomicLong ids = new AtomicLong(500);
            when(recommendations.saveAll(anyList())).thenAnswer(inv -> {
                List<Recommendation> list = inv.getArgument(0);
                list.forEach(r -> r.setId(ids.incrementAndGet()));
                saved.addAll(list);
                return list;
            });
        }

        @Test
        @DisplayName("A1: guarda todas las opciones, muestra 5 y avisa que hay mas")
        void storesAllShowsFive() {
            RecommendationBatch batch = service.request(ANA, simple());

            assertThat(batch.requestId()).isEqualTo(100L);
            assertThat(batch.items()).hasSize(RecommendationService.PAGE);
            assertThat(batch.hasMore()).isTrue();
            assertThat(batch.relaxedFilters()).containsExactly("CATEGORY");
            assertThat(batch.selectionSource()).isEqualTo("CATALOG");
            assertThat(saved).hasSize(8);
            assertThat(saved).extracting(Recommendation::getPosition).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
            assertThat(saved).filteredOn(Recommendation::getShown).hasSize(5);
            assertThat(batch.items().get(0).startsAt()).isEqualTo(START);
            assertThat(batch.items().get(0).endsAt()).isEqualTo(START.plusSeconds(3600));
            assertThat(batch.items().get(0).reason()).isEqualTo("frase 1");
        }

        @Test
        @DisplayName("A9: con 'cerca de nosotros' usa la ubicacion para buscar pero no la guarda")
        void nearUsUsesLocationWithoutStoringIt() {
            service.request(ANA, params(ZoneType.NEAR_US, null, 19.43, -99.13, START, END));

            ArgumentCaptor<RecommendationQuery> q = ArgumentCaptor.forClass(RecommendationQuery.class);
            verify(candidates).select(q.capture(), any(), anySet(), anyInt());
            assertThat(q.getValue().references()).hasSize(1);
            ArgumentCaptor<RecommendationRequest> r = ArgumentCaptor.forClass(RecommendationRequest.class);
            verify(requests).save(r.capture());
            assertThat(r.getValue().getZoneType()).isEqualTo(ZoneType.NEAR_US);
            assertThat(r.getValue().getBorough()).isNull();
        }

        @Test
        @DisplayName("con alcaldia mide la distancia desde su centro")
        void boroughUsesItsCenter() {
            when(boroughs.findByCode("COYOACAN"))
                    .thenReturn(Optional.of(Borough.builder().id(3L).code("COYOACAN").name("Coyoacán").lat(19.35).lon(-99.16).build()));

            service.request(ANA, params(ZoneType.BOROUGH, "COYOACAN", null, null, START, END));

            ArgumentCaptor<RecommendationQuery> q = ArgumentCaptor.forClass(RecommendationQuery.class);
            verify(candidates).select(q.capture(), any(), anySet(), anyInt());
            assertThat(q.getValue().references()).singleElement().satisfies(c -> assertThat(c.lat()).isEqualTo(19.35));
        }
    }

    @Nested
    class RequestRejected {

        @Test
        @DisplayName("A3: sin vinculo activo, 403")
        void noLink() {
            when(links.findActiveLinkByUserId(ANA)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.request(ANA, simple())).isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("A4: franja que ya no es libre para los dos, 400")
        void notMutual() {
            when(slots.isMutualFree(ANA, SAM, START, END)).thenReturn(false);
            assertThatThrownBy(() -> service.request(ANA, simple())).isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("A5: franja de menos de 60 minutos, 400")
        void tooShort() {
            Instant end = START.plusSeconds(59 * 60);
            when(slots.isMutualFree(ANA, SAM, START, end)).thenReturn(true);
            assertThatThrownBy(() -> service.request(ANA, params(ZoneType.NONE, null, null, null, START, end)))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("franja en el pasado o a mas de 14 dias, 400")
        void outOfWindow() {
            Instant past = Instant.parse("2026-10-17T10:00:00Z");
            Instant far = Instant.parse("2026-11-05T01:00:00Z");
            assertThatThrownBy(() -> service.request(ANA, params(ZoneType.NONE, null, null, null, past, past.plusSeconds(7200))))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> service.request(ANA, params(ZoneType.NONE, null, null, null, far, far.plusSeconds(7200))))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("A6: alcaldia faltante o inexistente, 400")
        void badBorough() {
            when(boroughs.findByCode("NARNIA")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.request(ANA, params(ZoneType.BOROUGH, null, null, null, START, END)))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> service.request(ANA, params(ZoneType.BOROUGH, "NARNIA", null, null, START, END)))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("A7: 'cerca de nosotros' sin ubicacion valida, 400")
        void nearUsWithoutLocation() {
            assertThatThrownBy(() -> service.request(ANA, params(ZoneType.NEAR_US, null, null, null, START, END)))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> service.request(ANA, params(ZoneType.NEAR_US, null, 99.0, -99.0, START, END)))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("A8: categoria desconocida, 400")
        void badCategory() {
            RecommendationParams p = new RecommendationParams(ZoneType.NONE, null, null, null, Scope.ANY, 0, "NADA", START, END);
            assertThatThrownBy(() -> service.request(ANA, p)).isInstanceOf(BadRequestException.class);
        }
    }

    /** Solicitud guardada con 12 opciones: 1-5 vistas, la 6 descartada sin verse, 7-12 sin ver. */
    @Nested
    class AfterRequest {

        private RecommendationRequest request;
        private List<Recommendation> stored;

        @BeforeEach
        void stubStored() {
            request = RecommendationRequest.builder().id(100L).requester(ana).link(link)
                    .scope(Scope.ANY).slotStart(START).slotEnd(END).status(RequestStatus.OPEN).build();
            stored = new ArrayList<>();
            for (int p = 1; p <= 12; p++) {
                stored.add(Recommendation.builder().id(500L + p).request(request).position(p).round(1)
                        .activity(activity(p, "Plan " + p, "BOARD_GAME", "HOME", 60))
                        .reason("frase " + p).reasonSource(ReasonSource.CATALOG)
                        .shown(p <= 5).discarded(p == 6).chosen(false).build());
            }
            when(requests.findLockedById(100L)).thenReturn(Optional.of(request));
            when(recommendations.findByRequestIdOrderByPositionAsc(100L)).thenReturn(stored);
            stored.forEach(r -> when(recommendations.findById(r.getId())).thenReturn(Optional.of(r)));
        }

        @Test
        @DisplayName("A11: buscar mas trae las siguientes 5 sin repetir ni descartadas")
        void moreShowsNextFive() {
            RecommendationBatch batch = service.more(ANA, 100L);

            assertThat(batch.items()).extracting(i -> i.title()).containsExactly("Plan 7", "Plan 8", "Plan 9", "Plan 10", "Plan 11");
            assertThat(batch.hasMore()).isTrue();
            assertThat(stored.get(6).getShown()).isTrue();
            verify(candidates, never()).select(any(), any(), anySet(), anyInt());
        }

        @Test
        @DisplayName("A12: sin opciones guardadas por ver, 409 (ajustar filtros)")
        void moreWhenExhausted() {
            stored.forEach(r -> r.setShown(true));
            assertThatThrownBy(() -> service.more(ANA, 100L)).isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("A13: la solicitud de otra persona, 403")
        void moreNotOwner() {
            assertThatThrownBy(() -> service.more(SAM, 100L)).isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("descartar marca la opcion y entrega la siguiente no vista")
        void discardReplaces() {
            DiscardResponse r = service.discard(ANA, 503L);

            assertThat(stored.get(2).getDiscarded()).isTrue();
            assertThat(r.replacement().title()).isEqualTo("Plan 7");
            assertThat(stored.get(6).getShown()).isTrue();
            assertThat(r.hasMore()).isTrue();
        }

        @Test
        @DisplayName("descartar cuando ya no quedan: sin reemplazo")
        void discardWithoutReplacement() {
            stored.forEach(r -> r.setShown(true));
            DiscardResponse r = service.discard(ANA, 503L);
            assertThat(r.replacement()).isNull();
            assertThat(r.hasMore()).isFalse();
        }

        @Test
        @DisplayName("A15: invitar crea el evento pendiente con la actividad y cierra la solicitud")
        void inviteCreatesPendingEvent() {
            Recommendation cine = stored.get(1);
            cine.setActivity(activity(2, "Cine", "FILM", "CITY", 240));
            cine.setPlace(Place.builder().id(30L).name("Cineteca").borough("Coyoacán").build());
            when(events.createEvent(eq(ANA), any())).thenReturn(CreateEventResponse.builder().success(true).build());

            service.invite(ANA, 502L);

            ArgumentCaptor<CreateEventRequest> e = ArgumentCaptor.forClass(CreateEventRequest.class);
            verify(events).createEvent(eq(ANA), e.capture());
            assertThat(e.getValue().getTitle()).isEqualTo("Cine");
            assertThat(e.getValue().getStartDateTime()).isEqualTo(START.toString());
            assertThat(e.getValue().getEndDateTime()).isEqualTo(END.toString()); // 240 min se recorta a la franja
            assertThat(e.getValue().getLocation()).isEqualTo("Cineteca · Coyoacán");
            assertThat(e.getValue().getDescription()).isEqualTo("frase 2");
            assertThat(e.getValue().getCategory()).isEqualTo("Social");
            assertThat(e.getValue().getActivityId()).isEqualTo(2L);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.SENT);
            assertThat(cine.getChosen()).isTrue();
        }

        @Test
        @DisplayName("una actividad en casa se invita con 'En casa'")
        void inviteHome() {
            when(events.createEvent(eq(ANA), any())).thenReturn(CreateEventResponse.builder().success(true).build());
            service.invite(ANA, 501L);
            ArgumentCaptor<CreateEventRequest> e = ArgumentCaptor.forClass(CreateEventRequest.class);
            verify(events).createEvent(eq(ANA), e.capture());
            assertThat(e.getValue().getLocation()).isEqualTo("En casa");
        }

        @Test
        @DisplayName("A16: una segunda invitacion de la misma solicitud, 409")
        void secondInvite() {
            request.setStatus(RequestStatus.SENT);
            assertThatThrownBy(() -> service.invite(ANA, 501L)).isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("A17: invitar una descartada, 400")
        void inviteDiscarded() {
            assertThatThrownBy(() -> service.invite(ANA, 506L)).isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("A18: invitar la recomendacion de otra persona, 403")
        void inviteNotOwner() {
            assertThatThrownBy(() -> service.invite(SAM, 501L)).isInstanceOf(ForbiddenException.class);
            verify(events, never()).createEvent(any(), any());
        }
    }
}
