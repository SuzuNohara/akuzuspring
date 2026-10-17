package com.nexus.service.recommendation;

import com.nexus.dto.BoroughResponse;
import com.nexus.dto.CreateEventRequest;
import com.nexus.dto.CreateEventResponse;
import com.nexus.dto.DiscardResponse;
import com.nexus.dto.RecommendationBatch;
import com.nexus.dto.RecommendationItem;
import com.nexus.dto.RecommendationParams;
import com.nexus.entity.Activity;
import com.nexus.entity.Borough;
import com.nexus.entity.Link;
import com.nexus.entity.Place;
import com.nexus.entity.Recommendation;
import com.nexus.entity.RecommendationRequest;
import com.nexus.exception.BadRequestException;
import com.nexus.exception.ConflictException;
import com.nexus.exception.ForbiddenException;
import com.nexus.exception.ResourceNotFoundException;
import com.nexus.model.IdeaCategory;
import com.nexus.model.RecommendationMode;
import com.nexus.model.RequestStatus;
import com.nexus.model.ZoneType;
import com.nexus.repository.BoroughRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.repository.PlaceRepository;
import com.nexus.repository.RecommendationRepository;
import com.nexus.repository.RecommendationRequestRepository;
import com.nexus.repository.UserRepository;
import com.nexus.service.EventService;
import com.nexus.service.ProximityFilter.Coordinates;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * U9 - Recomendaciones en la modalidad individual (RF-33, RF-37, RF-40; CU36, CU39, CU40).
 *
 * <p>Cada solicitud selecciona una sola vez hasta {@link #STORED} opciones y las guarda; la app las
 * va mostrando de {@link #PAGE} en {@link #PAGE}. "Buscar mas opciones" y descartar solo recorren
 * lo guardado (no vuelven a seleccionar): agotadas, la persona ajusta filtros y hace otra solicitud.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    /** Opciones que se seleccionan y guardan por solicitud (se prefetch para ahorrar al agente). */
    static final int STORED = 15;
    /** Opciones visibles a la vez. */
    static final int PAGE = 5;

    private static final int MIN_SLOT_MINUTES = 60;
    private static final String AT_HOME = "En casa";

    private final LinkRepository links;
    private final UserRepository users;
    private final BoroughRepository boroughs;
    private final PlaceRepository places;
    private final RecommendationRequestRepository requests;
    private final RecommendationRepository recommendations;
    private final CandidateSource candidates;
    private final ReasonWriter writer;
    private final CoupleContextService contexts;
    private final MutualSlotChecker slots;
    private final EventService events;
    private final Clock clock;

    /** Las 16 alcaldias para elegir zona, en orden alfabetico (sin coordenadas). */
    @Transactional(readOnly = true)
    public List<BoroughResponse> boroughs() {
        return boroughs.findAllByOrderByNameAsc().stream().map(b -> new BoroughResponse(b.getCode(), b.getName())).toList();
    }

    /** Pide recomendaciones: valida, selecciona {@link #STORED}, guarda y muestra las primeras. */
    @Transactional
    public RecommendationBatch request(Long userId, RecommendationParams p) {
        Link link = links.findActiveLinkByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("Necesitas un vínculo activo para pedir recomendaciones."));
        validateSlot(userId, partnerOf(link, userId), p.slotStart(), p.slotEnd());
        IdeaCategory category = parseCategory(p.category());
        Optional<Borough> borough = boroughOf(p);
        RecommendationQuery query = new RecommendationQuery(
                p.scope(), p.maxCostMxn(), category, p.slotStart(), p.slotEnd(), referencesOf(p, borough));
        CoupleContext context = contexts.build(userId, link);
        Selection selection = candidates.select(query, context, Set.of(), STORED);
        List<WrittenReason> reasons = writer.write(selection.candidates(), context);
        RecommendationRequest saved = requests.save(newRequest(userId, link, p, category, borough.orElse(null)));
        List<Recommendation> stored = recommendations.saveAll(toRecommendations(saved, selection, reasons));
        log.info("Solicitud {}: {} opciones ({}), filtros ampliados {}",
                saved.getId(), stored.size(), selection.source(), selection.relaxed());
        List<RecommendationItem> visible = stored.stream().filter(Recommendation::getShown).map(RecommendationService::toItem).toList();
        Set<String> relaxed = selection.relaxed().stream().map(Enum::name).collect(Collectors.toSet());
        return new RecommendationBatch(saved.getId(), visible, relaxed, selection.source().name(), stored.size() > PAGE);
    }

    /** "Buscar mas opciones": las siguientes {@link #PAGE} guardadas que no se han visto. */
    @Transactional
    public RecommendationBatch more(Long userId, Long requestId) {
        RecommendationRequest request = openRequestOf(userId, requestId);
        List<Recommendation> unseen = unseen(request);
        if (unseen.isEmpty()) {
            throw new ConflictException("Ya viste todas las opciones. Ajusta los filtros para ver más.");
        }
        List<Recommendation> next = unseen.subList(0, Math.min(PAGE, unseen.size()));
        next.forEach(r -> r.setShown(true));
        List<RecommendationItem> items = next.stream().map(RecommendationService::toItem).toList();
        return new RecommendationBatch(requestId, items, Set.of(), null, unseen.size() > next.size());
    }

    /** Descarta una opcion y entrega la siguiente guardada, si queda. */
    @Transactional
    public DiscardResponse discard(Long userId, Long recommendationId) {
        Recommendation rec = ownedRecommendation(userId, recommendationId);
        RecommendationRequest request = openRequestOf(userId, rec.getRequest().getId());
        rec.setDiscarded(true);
        List<Recommendation> unseen = unseen(request);
        if (unseen.isEmpty()) {
            return new DiscardResponse(null, false);
        }
        Recommendation replacement = unseen.get(0);
        replacement.setShown(true);
        return new DiscardResponse(toItem(replacement), unseen.size() > 1);
    }

    /** CU39 - Envia una opcion como invitacion: evento pendiente de aprobar por la pareja. */
    @Transactional
    public CreateEventResponse invite(Long userId, Long recommendationId) {
        Recommendation rec = ownedRecommendation(userId, recommendationId);
        if (Boolean.TRUE.equals(rec.getDiscarded())) {
            throw new BadRequestException("Esa opción ya la descartaste.");
        }
        RecommendationRequest request = openRequestOf(userId, rec.getRequest().getId());
        CreateEventResponse response = events.createEvent(userId, toEvent(rec, request));
        request.setStatus(RequestStatus.SENT);
        rec.setChosen(true);
        return response;
    }

    // --- validacion -------------------------------------------------------------------------

    private void validateSlot(Long userId, Long partnerId, Instant start, Instant end) {
        Instant now = clock.instant();
        if (!start.isAfter(now) || start.isAfter(now.plus(Duration.ofDays(MutualSlotChecker.DAYS_AHEAD)))) {
            throw new BadRequestException("Elige un horario de los próximos 14 días.");
        }
        if (Duration.between(start, end).toMinutes() < MIN_SLOT_MINUTES) {
            throw new BadRequestException("El horario debe durar al menos 60 minutos.");
        }
        if (!slots.isMutualFree(userId, partnerId, start, end)) {
            throw new BadRequestException("Ese horario ya no está libre para los dos.");
        }
    }

    private static IdeaCategory parseCategory(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return IdeaCategory.parse(code).orElseThrow(() -> new BadRequestException("Tipo de plan desconocido."));
    }

    private Optional<Borough> boroughOf(RecommendationParams p) {
        if (p.zoneType() != ZoneType.BOROUGH) {
            return Optional.empty();
        }
        if (p.boroughCode() == null || p.boroughCode().isBlank()) {
            throw new BadRequestException("Elige una alcaldía.");
        }
        return Optional.of(boroughs.findByCode(p.boroughCode())
                .orElseThrow(() -> new BadRequestException("Alcaldía desconocida.")));
    }

    private static List<Coordinates> referencesOf(RecommendationParams p, Optional<Borough> borough) {
        return switch (p.zoneType()) {
            case NONE -> List.of();
            case BOROUGH -> List.of(new Coordinates(borough.orElseThrow().getLat(), borough.orElseThrow().getLon()));
            case NEAR_US -> List.of(locationOf(p));
        };
    }

    private static Coordinates locationOf(RecommendationParams p) {
        if (p.lat() == null || p.lon() == null) {
            throw new BadRequestException("No recibimos tu ubicación.");
        }
        try {
            return new Coordinates(p.lat(), p.lon());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("La ubicación no es válida.");
        }
    }

    // --- acceso -----------------------------------------------------------------------------

    private RecommendationRequest openRequestOf(Long userId, Long requestId) {
        RecommendationRequest request = requests.findLockedById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada"));
        if (!request.getRequester().getId().equals(userId)) {
            throw new ForbiddenException("Esta solicitud no es tuya.");
        }
        if (request.getStatus() == RequestStatus.SENT) {
            throw new ConflictException("Ya enviaste una invitación con estas opciones.");
        }
        return request;
    }

    private Recommendation ownedRecommendation(Long userId, Long recommendationId) {
        Recommendation rec = recommendations.findById(recommendationId)
                .orElseThrow(() -> new ResourceNotFoundException("Recomendación no encontrada"));
        if (!rec.getRequest().getRequester().getId().equals(userId)) {
            throw new ForbiddenException("Esta recomendación no es tuya.");
        }
        return rec;
    }

    private List<Recommendation> unseen(RecommendationRequest request) {
        return recommendations.findByRequestIdOrderByPositionAsc(request.getId()).stream()
                .filter(r -> !r.getShown() && !r.getDiscarded())
                .toList();
    }

    private static Long partnerOf(Link link, Long userId) {
        Long initiator = link.getInitiatorUser().getId();
        return initiator.equals(userId) ? link.getPartnerUser().getId() : initiator;
    }

    // --- construccion -----------------------------------------------------------------------

    private RecommendationRequest newRequest(
            Long userId, Link link, RecommendationParams p, IdeaCategory category, Borough borough) {
        return RecommendationRequest.builder()
                .requester(users.getReferenceById(userId))
                .link(link)
                .mode(RecommendationMode.INDIVIDUAL)
                .zoneType(p.zoneType())
                .borough(borough)
                .scope(p.scope())
                .maxCostMxn(p.maxCostMxn())
                .category(category == null ? null : category.name())
                .slotStart(p.slotStart())
                .slotEnd(p.slotEnd())
                .build();
    }

    private List<Recommendation> toRecommendations(
            RecommendationRequest request, Selection selection, List<WrittenReason> reasons) {
        List<Recommendation> out = new ArrayList<>();
        for (int i = 0; i < selection.candidates().size(); i++) {
            Candidate c = selection.candidates().get(i);
            WrittenReason reason = reasons.get(i);
            out.add(Recommendation.builder()
                    .request(request)
                    .activity(c.activity())
                    .place(c.place() == null ? null : places.getReferenceById(c.place().placeId()))
                    .round(1)
                    .position(i + 1)
                    .reason(reason.reason())
                    .reasonSource(reason.source())
                    .shown(i < PAGE)
                    .build());
        }
        return out;
    }

    private static RecommendationItem toItem(Recommendation r) {
        Activity a = r.getActivity();
        IdeaCategory category = IdeaCategory.ofActivityType(a.getActivityType());
        Place place = r.getPlace();
        Instant start = r.getRequest().getSlotStart();
        return new RecommendationItem(
                r.getId(), a.getId(), a.getTitle(), category.name(), category.label(), a.getLocationScope(),
                place == null ? null : place.getName(), place == null ? null : place.getBorough(),
                a.getDurationAvg(), a.getCostMxnPp(), r.getReason(), r.getReasonSource(),
                start, endOf(a, r.getRequest()));
    }

    private static Instant endOf(Activity a, RecommendationRequest request) {
        Instant byDuration = request.getSlotStart().plus(Duration.ofMinutes(a.getDurationAvg()));
        return byDuration.isAfter(request.getSlotEnd()) ? request.getSlotEnd() : byDuration;
    }

    private static CreateEventRequest toEvent(Recommendation rec, RecommendationRequest request) {
        Activity a = rec.getActivity();
        Place place = rec.getPlace();
        return CreateEventRequest.builder()
                .title(a.getTitle())
                .startDateTime(request.getSlotStart().toString())
                .endDateTime(endOf(a, request).toString())
                .location(place == null ? AT_HOME : place.getName() + " · " + place.getBorough())
                .category(IdeaCategory.ofActivityType(a.getActivityType()).label())
                .description(rec.getReason())
                .activityId(a.getId())
                .build();
    }
}
