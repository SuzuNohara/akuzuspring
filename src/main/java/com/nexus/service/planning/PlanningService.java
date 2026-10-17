package com.nexus.service.planning;

import com.nexus.dto.CreateEventResponse;
import com.nexus.dto.RecommendationBatch;
import com.nexus.dto.planning.MessageView;
import com.nexus.dto.planning.PlanningFilters;
import com.nexus.dto.planning.PlanningMember;
import com.nexus.dto.planning.PlanningOption;
import com.nexus.dto.planning.PlanningView;
import com.nexus.dto.planning.ProposalView;
import com.nexus.dto.planning.ReadyRequest;
import com.nexus.entity.Borough;
import com.nexus.entity.Link;
import com.nexus.entity.PlanningMessage;
import com.nexus.entity.PlanningSession;
import com.nexus.entity.PlanningVote;
import com.nexus.entity.Recommendation;
import com.nexus.entity.RecommendationRequest;
import com.nexus.exception.BadRequestException;
import com.nexus.exception.ConflictException;
import com.nexus.exception.ForbiddenException;
import com.nexus.exception.ResourceNotFoundException;
import com.nexus.model.IdeaCategory;
import com.nexus.model.PlanningStatus;
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
import com.nexus.service.ProximityFilter.Coordinates;
import com.nexus.service.recommendation.CoupleCriteria;
import com.nexus.service.recommendation.RecommendationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-38 / RF-39 - Planeacion en pareja (pantallas B1-B9): invitar, unirse, elegir filtros juntos,
 * votar opciones, proponer y aceptar la cita, y el chat de la sesion.
 *
 * <p>Reglas nuevas de la usuaria (2026-10-16): invitacion de 24 h; sesion de 12 h desde que la pareja
 * acepta; 20 mensajes por hora por persona y 140 por chat, borrados al cerrar; 15 opciones
 * guardadas que se ven de 5 en 5; al aceptar una propuesta el evento queda aprobado por los dos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanningService {

    static final List<PlanningStatus> OPEN = List.of(PlanningStatus.INVITED, PlanningStatus.ACTIVE);
    static final int DEFAULT_BUDGET = 1_500;
    private static final int PAGE = 5;
    private static final Duration SHOW_ENDED_FOR = Duration.ofDays(7);
    private static final Duration FILTER_GROUP = Duration.ofMinutes(1);
    private static final Duration MESSAGE_GROUP = Duration.ofHours(1);
    private static final ZoneId CDMX = ZoneId.of("America/Mexico_City");
    private static final String[] DAYS = {"lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};

    // Tipos de aviso (B9)
    static final String T_INVITE = "INVITE";
    static final String T_JOINED = "JOINED";
    static final String T_CANCELLED = "CANCELLED";
    static final String T_FILTERS = "FILTERS";
    static final String T_READY = "READY";
    static final String T_OPTIONS = "OPTIONS";
    static final String T_MATCH = "MATCH";
    static final String T_PROPOSAL = "PROPOSAL";
    static final String T_DECLINED = "DECLINED";
    static final String T_AGREED = "AGREED";
    static final String T_MESSAGES = "MESSAGES";
    static final String T_REMINDER = "REMINDER";
    static final String T_EXPIRED = "EXPIRED";
    static final String T_CLOSED = "CLOSED";

    private final PlanningSessionRepository sessions;
    private final PlanningVoteRepository votes;
    private final PlanningMessageRepository messages;
    private final LinkRepository links;
    private final UserRepository users;
    private final BoroughRepository boroughs;
    private final RecommendationRepository recommendations;
    private final RecommendationRequestRepository requests;
    private final RecommendationService recommendationService;
    private final EventService events;
    private final AppNotificationService notifications;
    private final SessionLocations locations;
    private final Clock clock;

    // --- invitacion (B1) --------------------------------------------------------------------

    @Transactional
    public PlanningView invite(Long userId) {
        Link link = activeLink(userId);
        Optional<PlanningSession> open = sessions.findFirstByLinkIdAndStatusInOrderByIdDesc(link.getId(), OPEN)
                .map(this::expireIfDue)
                .filter(s -> s.getStatus().isOpen());
        if (open.isPresent()) {
            throw new ConflictException("Ya tienen una planeación abierta.");
        }
        Instant now = clock.instant();
        Long partnerId = partnerOf(link, userId);
        PlanningSession s = sessions.save(PlanningSession.builder()
                .linkId(link.getId())
                .inviterId(userId)
                .partnerId(partnerId)
                .status(PlanningStatus.INVITED)
                .inviteExpiresAt(PlanningRules.inviteExpiry(now))
                .inviterSeenAt(now)
                .build());
        notifications.notify(partnerId, s.getId(), T_INVITE,
                nameOf(userId) + " te invita a planear una cita", "Únete para planear juntos.");
        return view(s, userId, Set.of());
    }

    /** La planeacion abierta del vinculo, o la ultima que termino hace poco (para avisar como). */
    @Transactional
    public Optional<PlanningView> current(Long userId) {
        Optional<Link> link = links.findActiveLinkByUserId(userId);
        if (link.isEmpty()) {
            return Optional.empty();
        }
        Optional<PlanningSession> latest = sessions.findFirstByLinkIdOrderByIdDesc(link.get().getId());
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        PlanningSession s = expireIfDue(latest.get());
        Instant now = clock.instant();
        if (!s.getStatus().isOpen() && (s.getClosedAt() == null || s.getClosedAt().isBefore(now.minus(SHOW_ENDED_FOR)))) {
            return Optional.empty();
        }
        markSeen(s, userId, now);
        return Optional.of(view(s, userId, Set.of()));
    }

    @Transactional
    public PlanningView accept(Long userId, Long sessionId) {
        PlanningSession s = memberSession(userId, sessionId);
        if (!s.getPartnerId().equals(userId)) {
            throw new ForbiddenException("Solo tu pareja puede unirse a esta planeación.");
        }
        requireStatus(s, PlanningStatus.INVITED, "Esta invitación ya no está disponible.");
        Instant now = clock.instant();
        s.setStatus(PlanningStatus.ACTIVE);
        s.setAcceptedAt(now);
        s.setSessionExpiresAt(PlanningRules.sessionExpiry(now));
        s.setScope(Scope.ANY);
        s.setMaxCostMxn(DEFAULT_BUDGET);
        notifications.notify(s.getInviterId(), s.getId(), T_JOINED,
                nameOf(userId) + " se unió a la planeación", "Ya pueden planear juntos.");
        return view(s, userId, Set.of());
    }

    @Transactional
    public PlanningView cancel(Long userId, Long sessionId) {
        PlanningSession s = memberSession(userId, sessionId);
        if (!s.getInviterId().equals(userId)) {
            throw new ForbiddenException("Solo quien invitó puede cancelar la invitación.");
        }
        requireStatus(s, PlanningStatus.INVITED, "La invitación ya no se puede cancelar.");
        s.setStatus(PlanningStatus.CANCELLED);
        s.setClosedAt(clock.instant());
        notifications.notify(s.getPartnerId(), s.getId(), T_CANCELLED,
                nameOf(userId) + " canceló la invitación", "Ya no está disponible.");
        return view(s, userId, Set.of());
    }

    // --- filtros (B2) -----------------------------------------------------------------------

    @Transactional
    public PlanningView updateFilters(Long userId, Long sessionId, PlanningFilters f) {
        PlanningSession s = activeSession(userId, sessionId);
        requirePhase(s, "FILTERS", "Primero ajusten los filtros.");
        Optional<String> change = describeChange(s, f, nameOf(userId));
        applyFilters(s, f);
        if (change.isPresent()) {
            s.setInviterReady(false);
            s.setPartnerReady(false);
            notifications.notifyGrouped(s.otherThan(userId), s.getId(), T_FILTERS,
                    nameOf(userId) + " cambió los filtros", change.get(), FILTER_GROUP);
        }
        return view(s, userId, Set.of());
    }

    /** "Listo": si la pareja ya estaba lista, busca las opciones de los dos. */
    @Transactional
    public PlanningView ready(Long userId, Long sessionId, ReadyRequest location) {
        PlanningSession s = activeSession(userId, sessionId);
        requirePhase(s, "FILTERS", "Primero ajusten los filtros.");
        requireCompleteFilters(s);
        if (s.getZoneType() == ZoneType.NEAR_US) {
            locations.put(s.getId(), userId, coordinatesOf(location));
        }
        s.setReady(userId, true);
        Long other = s.otherThan(userId);
        if (!s.isReady(other)) {
            notifications.notify(other, s.getId(), T_READY,
                    nameOf(userId) + " terminó de elegir los filtros", "Toca \"Listo\" para buscar opciones juntos.");
            return view(s, userId, Set.of());
        }
        RecommendationBatch batch = search(s);
        notifications.notify(other, s.getId(), T_OPTIONS, "Ya tienen opciones", "Nexus encontró planes para los dos.");
        return view(s, userId, batch.relaxedFilters());
    }

    @Transactional
    public PlanningView unready(Long userId, Long sessionId) {
        PlanningSession s = activeSession(userId, sessionId);
        requirePhase(s, "FILTERS", "Primero ajusten los filtros.");
        s.setReady(userId, false);
        return view(s, userId, Set.of());
    }

    /** B7 - Agotadas las opciones, vuelven a los filtros; la pareja ve quien los esta ajustando. */
    @Transactional
    public PlanningView adjust(Long userId, Long sessionId) {
        PlanningSession s = activeSession(userId, sessionId);
        requirePhase(s, "OPTIONS", "No hay opciones que ajustar.");
        s.setAdjustingUserId(userId);
        s.setInviterReady(false);
        s.setPartnerReady(false);
        return view(s, userId, Set.of());
    }

    // --- opciones (B3) ----------------------------------------------------------------------

    @Transactional
    public PlanningView vote(Long userId, Long sessionId, Long recommendationId, Vote vote) {
        PlanningSession s = activeSession(userId, sessionId);
        requirePhase(s, "OPTIONS", "Ahora no se puede votar.");
        Recommendation rec = visibleRecommendation(s, recommendationId);
        Long other = s.otherThan(userId);
        Optional<Vote> before = voteOf(rec.getId(), userId);
        votes.save(PlanningVote.builder().recommendationId(rec.getId()).userId(userId).sessionId(s.getId()).vote(vote).build());
        Optional<Vote> theirs = voteOf(rec.getId(), other);
        if (vote == Vote.DISLIKE && theirs.equals(Optional.of(Vote.DISLIKE))) {
            rec.setDiscarded(true);
            unseen(s).stream().findFirst().ifPresent(next -> next.setShown(true));
        } else if (vote == Vote.LIKE && theirs.equals(Optional.of(Vote.LIKE)) && !before.equals(Optional.of(Vote.LIKE))) {
            notifications.notify(other, s.getId(), T_MATCH, "¡Coinciden!",
                    "A los dos les gusta \"" + rec.getActivity().getTitle() + "\".");
        }
        return view(s, userId, Set.of());
    }

    /** "Buscar mas opciones": las siguientes guardadas, sin volver a seleccionar. */
    @Transactional
    public PlanningView more(Long userId, Long sessionId) {
        PlanningSession s = activeSession(userId, sessionId);
        requirePhase(s, "OPTIONS", "Ahora no se pueden buscar más opciones.");
        List<Recommendation> next = unseen(s);
        if (next.isEmpty()) {
            throw new ConflictException("Ajusten los filtros para ver más opciones.");
        }
        visible(s).forEach(r -> r.setDiscarded(true));
        next.subList(0, Math.min(PAGE, next.size())).forEach(r -> r.setShown(true));
        return view(s, userId, Set.of());
    }

    // --- propuesta (B4, B5) -----------------------------------------------------------------

    @Transactional
    public PlanningView propose(Long userId, Long sessionId, Long recommendationId) {
        PlanningSession s = activeSession(userId, sessionId);
        requirePhase(s, "OPTIONS", "Ya hay una propuesta activa.");
        Recommendation rec = visibleRecommendation(s, recommendationId);
        s.setProposalRecommendationId(rec.getId());
        s.setProposedById(userId);
        notifications.notify(s.otherThan(userId), s.getId(), T_PROPOSAL, nameOf(userId) + " te propuso una cita",
                "\"" + rec.getActivity().getTitle() + "\", " + dayAndTime(rec.getRequest().getSlotStart()) + ".");
        return view(s, userId, Set.of());
    }

    @Transactional
    public PlanningView withdraw(Long userId, Long sessionId) {
        PlanningSession s = activeSession(userId, sessionId);
        requirePhase(s, "PROPOSAL", "No hay propuesta activa.");
        if (!userId.equals(s.getProposedById())) {
            throw new ForbiddenException("Solo quien propuso puede retirar la propuesta.");
        }
        clearProposal(s);
        return view(s, userId, Set.of());
    }

    /** "Seguir buscando": la pareja no acepta la propuesta y vuelven a las opciones. */
    @Transactional
    public PlanningView rejectProposal(Long userId, Long sessionId) {
        PlanningSession s = activeSession(userId, sessionId);
        requirePhase(s, "PROPOSAL", "No hay propuesta activa.");
        if (userId.equals(s.getProposedById())) {
            throw new ForbiddenException("Tu pareja es quien decide tu propuesta.");
        }
        Long proposer = s.getProposedById();
        clearProposal(s);
        notifications.notify(proposer, s.getId(), T_DECLINED,
                nameOf(userId) + " quiere seguir buscando", "Pueden proponer otra opción.");
        return view(s, userId, Set.of());
    }

    /** Acuerdo: el evento se crea aprobado por los dos, la sesion se cierra y el chat se borra. */
    @Transactional
    public PlanningView acceptProposal(Long userId, Long sessionId) {
        PlanningSession s = activeSession(userId, sessionId);
        requirePhase(s, "PROPOSAL", "No hay propuesta activa.");
        if (userId.equals(s.getProposedById())) {
            throw new ForbiddenException("Tu pareja es quien acepta tu propuesta.");
        }
        Recommendation rec = recommendations.findById(s.getProposalRecommendationId())
                .orElseThrow(() -> new ResourceNotFoundException("Opción no encontrada"));
        RecommendationRequest request = rec.getRequest();
        CreateEventResponse created = events.createEvent(s.getProposedById(), RecommendationService.toEvent(rec, request));
        Long eventId = created.getEvent().getId();
        events.approveEvent(userId, eventId);
        request.setStatus(RequestStatus.SENT);
        rec.setChosen(true);
        s.setStatus(PlanningStatus.AGREED);
        s.setClosedAt(clock.instant());
        s.setEventId(eventId);
        messages.deleteBySessionId(s.getId());
        notifications.notify(s.getProposedById(), s.getId(), T_AGREED, "¡Cita confirmada!",
                nameOf(userId) + " aceptó tu propuesta para el " + dayOf(request.getSlotStart()) + ".");
        log.info("Planeacion {}: acuerdo, evento {}", s.getId(), eventId);
        return view(s, userId, Set.of());
    }

    // --- chat (B6) --------------------------------------------------------------------------

    @Transactional
    public List<MessageView> messages(Long userId, Long sessionId) {
        PlanningSession s = memberSession(userId, sessionId);
        if (s.getStatus() != PlanningStatus.ACTIVE) {
            return List.of();
        }
        markChatSeen(s, userId, clock.instant());
        Map<Long, String> names = Map.of(s.getInviterId(), nameOf(s.getInviterId()), s.getPartnerId(), nameOf(s.getPartnerId()));
        return messages.findBySessionIdOrderByCreatedAtAsc(s.getId()).stream()
                .map(m -> new MessageView(m.getId(), m.getSenderId().equals(userId), names.get(m.getSenderId()),
                        m.getBody(), m.getCreatedAt()))
                .toList();
    }

    @Transactional
    public List<MessageView> sendMessage(Long userId, Long sessionId, String body) {
        PlanningSession s = activeSession(userId, sessionId);
        String text = PlanningRules.cleanMessage(body)
                .orElseThrow(() -> new BadRequestException("Escribe un mensaje de hasta 500 caracteres."));
        Instant now = clock.instant();
        long lastHour = messages.countBySessionIdAndSenderIdAndCreatedAtAfter(s.getId(), userId, now.minus(Duration.ofHours(1)));
        PlanningRules.messageRejection(lastHour, messages.countBySessionId(s.getId())).ifPresent(reason -> {
            throw new ConflictException(reason);
        });
        messages.save(PlanningMessage.builder().sessionId(s.getId()).senderId(userId).body(text).createdAt(now).build());
        Long other = s.otherThan(userId);
        long unread = messages.countBySessionIdAndSenderIdAndCreatedAtAfter(s.getId(), userId, chatSeenOrEpoch(s, other));
        notifications.notifyGrouped(other, s.getId(), T_MESSAGES, "Mensajes nuevos de " + nameOf(userId),
                unread + (unread == 1 ? " mensaje" : " mensajes") + " en su planeación.", MESSAGE_GROUP);
        return messages(userId, sessionId);
    }

    // --- plazos y recordatorios (B8, B9) ----------------------------------------------------

    /** Vence invitaciones y sesiones y manda los recordatorios que tocan (cada minuto). */
    @Transactional
    public void sweep() {
        Instant now = clock.instant();
        for (PlanningSession s : sessions.findByStatusIn(OPEN)) {
            expireIfDue(s);
            if (!s.getStatus().isOpen()) {
                continue;
            }
            PlanningRules.dueReminder(s).apply(now).ifPresent(r -> {
                remind(s, r);
                PlanningRules.markSent(s, r);
            });
        }
    }

    private void remind(PlanningSession s, PlanningRules.Reminder r) {
        switch (r) {
            case INVITE_12H, INVITE_1H -> notifications.notify(s.getPartnerId(), s.getId(), T_REMINDER, "Recordatorio",
                    "La invitación de " + nameOf(s.getInviterId()) + " vence en "
                            + (r == PlanningRules.Reminder.INVITE_12H ? "12 horas." : "1 hora."));
            case SESSION_6H, SESSION_1H, SESSION_15M -> {
                String title = switch (r) {
                    case SESSION_6H -> "Quedan 6 horas";
                    case SESSION_1H -> "Queda 1 hora";
                    default -> "Quedan 15 minutos";
                };
                for (Long member : List.of(s.getInviterId(), s.getPartnerId())) {
                    notifications.notify(member, s.getId(), T_REMINDER, title,
                            "Decidan su cita antes de que se cierre la sesión.");
                }
            }
        }
    }

    PlanningSession expireIfDue(PlanningSession s) {
        Instant now = clock.instant();
        if (s.getStatus() == PlanningStatus.INVITED && !now.isBefore(s.getInviteExpiresAt())) {
            s.setStatus(PlanningStatus.EXPIRED);
            s.setClosedAt(s.getInviteExpiresAt());
            notifications.notify(s.getInviterId(), s.getId(), T_EXPIRED,
                    nameOf(s.getPartnerId()) + " no aceptó a tiempo", "La invitación venció.");
            notifications.notify(s.getPartnerId(), s.getId(), T_EXPIRED,
                    "La invitación de " + nameOf(s.getInviterId()) + " venció", "Pueden planear otra cita cuando quieran.");
        } else if (s.getStatus() == PlanningStatus.ACTIVE && !now.isBefore(s.getSessionExpiresAt())) {
            s.setStatus(PlanningStatus.CLOSED);
            s.setClosedAt(s.getSessionExpiresAt());
            messages.deleteBySessionId(s.getId());
            locations.clear(s.getId());
            for (Long member : List.of(s.getInviterId(), s.getPartnerId())) {
                notifications.notify(member, s.getId(), T_CLOSED, "Se acabó el tiempo", "No llegaron a un acuerdo a tiempo.");
            }
        }
        return s;
    }

    // --- busqueda ---------------------------------------------------------------------------

    private RecommendationBatch search(PlanningSession s) {
        Link link = links.findById(s.getLinkId()).orElseThrow(() -> new ResourceNotFoundException("Vínculo no encontrado"));
        Borough borough = s.getBoroughId() == null ? null : boroughs.findById(s.getBoroughId()).orElse(null);
        List<Coordinates> references = switch (s.getZoneType()) {
            case NONE -> List.of();
            case BOROUGH -> List.of(new Coordinates(borough.getLat(), borough.getLon()));
            case NEAR_US -> locations.get(s.getId());
        };
        RecommendationBatch batch = recommendationService.requestForCouple(s.getInviterId(), link, new CoupleCriteria(
                s.getZoneType(), borough, s.getScope(), s.getMaxCostMxn(), categoriesOf(s),
                s.getSlotStart(), s.getSlotEnd(), references));
        s.setRequestId(batch.requestId());
        s.setAdjustingUserId(null);
        clearProposal(s);
        locations.clear(s.getId());
        return batch;
    }

    // --- vista ------------------------------------------------------------------------------

    private PlanningView view(PlanningSession s, Long userId, Set<String> relaxed) {
        Instant now = clock.instant();
        Long other = s.otherThan(userId);
        String phase = phaseOf(s);
        PlanningMember me = new PlanningMember(userId, nameOf(userId), true, s.isReady(userId));
        PlanningMember partner = new PlanningMember(other, nameOf(other),
                PlanningRules.isOnline(s.seenAt(other), now), s.isReady(other));

        List<Recommendation> stored = s.getRequestId() == null ? List.of()
                : recommendations.findByRequestIdOrderByPositionAsc(s.getRequestId());
        Map<Long, List<PlanningVote>> votesByRec = votes.findBySessionId(s.getId()).stream()
                .collect(Collectors.groupingBy(PlanningVote::getRecommendationId));
        Function<Recommendation, PlanningOption> toOption = r -> new PlanningOption(
                RecommendationService.toItem(r), voteName(votesByRec, r.getId(), userId), voteName(votesByRec, r.getId(), other));

        List<PlanningOption> options = "OPTIONS".equals(phase) || "PROPOSAL".equals(phase)
                ? stored.stream().filter(r -> r.getShown() && !r.getDiscarded()).map(toOption).toList()
                : List.of();
        boolean hasMore = stored.stream().anyMatch(r -> !r.getShown() && !r.getDiscarded());
        Optional<PlanningOption> chosen = Optional.ofNullable(s.getProposalRecommendationId())
                .flatMap(id -> stored.stream().filter(r -> r.getId().equals(id)).findFirst())
                .map(toOption);
        ProposalView proposal = "PROPOSAL".equals(phase)
                ? chosen.map(o -> new ProposalView(o, userId.equals(s.getProposedById()))).orElse(null)
                : null;
        List<String> activity = "FILTERS".equals(phase)
                ? notifications.recentBodies(userId, s.getId(), List.of(T_FILTERS, T_READY))
                : List.of();
        long unread = s.getStatus() == PlanningStatus.ACTIVE
                ? messages.countBySessionIdAndSenderIdAndCreatedAtAfter(s.getId(), other, chatSeenOrEpoch(s, userId))
                : 0;

        return new PlanningView(
                s.getId(), s.getStatus().name(), phase, s.getInviterId().equals(userId) ? "INVITER" : "PARTNER",
                me, partner, s.getInviteExpiresAt(), s.getSessionExpiresAt(), filtersOf(s), activity, options,
                hasMore, relaxed, other.equals(s.getAdjustingUserId()), proposal,
                s.getStatus() == PlanningStatus.AGREED ? chosen.orElse(null) : null, s.getEventId(), unread);
    }

    static String phaseOf(PlanningSession s) {
        if (s.getStatus() != PlanningStatus.ACTIVE) {
            return s.getStatus().name();
        }
        if (s.getProposalRecommendationId() != null) {
            return "PROPOSAL";
        }
        return s.getRequestId() == null || s.getAdjustingUserId() != null ? "FILTERS" : "OPTIONS";
    }

    private PlanningFilters filtersOf(PlanningSession s) {
        String boroughCode = s.getBoroughId() == null ? null
                : boroughs.findById(s.getBoroughId()).map(Borough::getCode).orElse(null);
        List<String> categories = categoriesOf(s).stream().map(Enum::name).sorted().toList();
        return new PlanningFilters(s.getZoneType(), boroughCode, s.getScope(), s.getMaxCostMxn(), categories,
                s.getSlotStart(), s.getSlotEnd());
    }

    private static String voteName(Map<Long, List<PlanningVote>> byRec, Long recId, Long userId) {
        return byRec.getOrDefault(recId, List.of()).stream()
                .filter(v -> v.getUserId().equals(userId))
                .map(v -> v.getVote().name())
                .findFirst()
                .orElse(null);
    }

    // --- filtros: aplicar y describir -------------------------------------------------------

    private void applyFilters(PlanningSession s, PlanningFilters f) {
        s.setZoneType(f.zoneType());
        s.setBoroughId(f.zoneType() == ZoneType.BOROUGH ? boroughIdOf(f.boroughCode()) : null);
        s.setScope(f.scope() == null ? Scope.ANY : f.scope());
        s.setMaxCostMxn(f.maxCostMxn() == null ? DEFAULT_BUDGET : f.maxCostMxn());
        s.setCategories(parseCategories(f.categories()).stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
        if ((f.slotStart() == null) != (f.slotEnd() == null)) {
            throw new BadRequestException("El horario necesita inicio y fin.");
        }
        s.setSlotStart(f.slotStart());
        s.setSlotEnd(f.slotEnd());
    }

    /** Primera diferencia, como frase para la pareja ("Sam cambió el presupuesto a $1,500"). */
    private Optional<String> describeChange(PlanningSession s, PlanningFilters f, String who) {
        if (f.zoneType() != s.getZoneType()
                || (f.zoneType() == ZoneType.BOROUGH && !Objects.equals(boroughIdOf(f.boroughCode()), s.getBoroughId()))) {
            return Optional.of(who + " eligió " + zoneLabel(f));
        }
        if (!Objects.equals(f.slotStart(), s.getSlotStart()) || !Objects.equals(f.slotEnd(), s.getSlotEnd())) {
            return Optional.of(who + " cambió el horario");
        }
        int budget = f.maxCostMxn() == null ? DEFAULT_BUDGET : f.maxCostMxn();
        if (!Objects.equals(budget, s.getMaxCostMxn())) {
            return Optional.of(who + " cambió el presupuesto a "
                    + (budget == 0 ? "solo gratis" : "$" + String.format(Locale.US, "%,d", budget)));
        }
        Scope scope = f.scope() == null ? Scope.ANY : f.scope();
        if (scope != s.getScope()) {
            return Optional.of(who + " eligió \"" + scopeLabel(scope) + "\"");
        }
        Set<IdeaCategory> before = categoriesOf(s);
        Set<IdeaCategory> after = parseCategories(f.categories());
        if (!before.equals(after)) {
            if (after.isEmpty()) {
                return Optional.of(who + " eligió cualquier tipo de plan");
            }
            Optional<IdeaCategory> added = after.stream().filter(c -> !before.contains(c)).findFirst();
            return added.map(c -> who + " agregó el plan " + c.label())
                    .or(() -> before.stream().filter(c -> !after.contains(c)).findFirst()
                            .map(c -> who + " quitó el plan " + c.label()));
        }
        return Optional.empty();
    }

    private String zoneLabel(PlanningFilters f) {
        if (f.zoneType() == null) {
            return "dónde";
        }
        return switch (f.zoneType()) {
            case NEAR_US -> "\"Cerca de nosotros\"";
            case NONE -> "\"Sin preferencia\"";
            case BOROUGH -> boroughs.findByCode(f.boroughCode()).map(Borough::getName).orElse("una alcaldía");
        };
    }

    private static String scopeLabel(Scope scope) {
        return switch (scope) {
            case HOME -> "En casa";
            case CITY -> "En la ciudad";
            case ANY -> "Cualquiera";
        };
    }

    private Long boroughIdOf(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return boroughs.findByCode(code).map(Borough::getId)
                .orElseThrow(() -> new BadRequestException("Alcaldía desconocida."));
    }

    private static Set<IdeaCategory> parseCategories(List<String> codes) {
        if (codes == null) {
            return Set.of();
        }
        Set<IdeaCategory> out = new LinkedHashSet<>();
        for (String code : codes) {
            out.add(IdeaCategory.parse(code).orElseThrow(() -> new BadRequestException("Tipo de plan desconocido.")));
        }
        return out;
    }

    private static Set<IdeaCategory> categoriesOf(PlanningSession s) {
        if (s.getCategories() == null || s.getCategories().isBlank()) {
            return Set.of();
        }
        return Arrays.stream(s.getCategories().split(","))
                .map(IdeaCategory::parse)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void requireCompleteFilters(PlanningSession s) {
        if (s.getZoneType() == null) {
            throw new BadRequestException("Elijan dónde.");
        }
        if (s.getZoneType() == ZoneType.BOROUGH && s.getBoroughId() == null) {
            throw new BadRequestException("Elijan una alcaldía.");
        }
        if (s.getSlotStart() == null) {
            throw new BadRequestException("Elijan cuándo.");
        }
    }

    private static Coordinates coordinatesOf(ReadyRequest location) {
        if (location == null || location.lat() == null || location.lon() == null) {
            throw new BadRequestException("Comparte tu ubicación para buscar cerca de ustedes.");
        }
        try {
            return new Coordinates(location.lat(), location.lon());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("La ubicación no es válida.");
        }
    }

    // --- acceso y estado --------------------------------------------------------------------

    private Link activeLink(Long userId) {
        return links.findActiveLinkByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("Necesitas un vínculo activo para planear en pareja."));
    }

    private PlanningSession memberSession(Long userId, Long sessionId) {
        PlanningSession s = sessions.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Planeación no encontrada"));
        if (!s.isMember(userId)) {
            throw new ForbiddenException("Esta planeación no es tuya.");
        }
        return expireIfDue(s);
    }

    private PlanningSession activeSession(Long userId, Long sessionId) {
        PlanningSession s = memberSession(userId, sessionId);
        requireStatus(s, PlanningStatus.ACTIVE, "Esta planeación ya terminó.");
        return s;
    }

    private static void requireStatus(PlanningSession s, PlanningStatus status, String message) {
        if (s.getStatus() != status) {
            throw new ConflictException(message);
        }
    }

    private static void requirePhase(PlanningSession s, String phase, String message) {
        if (!phase.equals(phaseOf(s))) {
            throw new ConflictException(message);
        }
    }

    private Recommendation visibleRecommendation(PlanningSession s, Long recommendationId) {
        return visible(s).stream().filter(r -> r.getId().equals(recommendationId)).findFirst()
                .orElseThrow(() -> new BadRequestException("Esa opción ya no está disponible."));
    }

    private List<Recommendation> visible(PlanningSession s) {
        return recommendations.findByRequestIdOrderByPositionAsc(s.getRequestId()).stream()
                .filter(r -> r.getShown() && !r.getDiscarded())
                .toList();
    }

    private List<Recommendation> unseen(PlanningSession s) {
        return new ArrayList<>(recommendations.findByRequestIdOrderByPositionAsc(s.getRequestId()).stream()
                .filter(r -> !r.getShown() && !r.getDiscarded())
                .toList());
    }

    private Optional<Vote> voteOf(Long recommendationId, Long userId) {
        return votes.findById(new PlanningVote.Key(recommendationId, userId)).map(PlanningVote::getVote);
    }

    private static void clearProposal(PlanningSession s) {
        s.setProposalRecommendationId(null);
        s.setProposedById(null);
    }

    private void markSeen(PlanningSession s, Long userId, Instant now) {
        if (s.getInviterId().equals(userId)) {
            sessions.markInviterSeen(s.getId(), now);
        } else {
            sessions.markPartnerSeen(s.getId(), now);
        }
    }

    private void markChatSeen(PlanningSession s, Long userId, Instant now) {
        if (s.getInviterId().equals(userId)) {
            sessions.markInviterChatSeen(s.getId(), now);
        } else {
            sessions.markPartnerChatSeen(s.getId(), now);
        }
    }

    private static Instant chatSeenOrEpoch(PlanningSession s, Long userId) {
        Instant seen = s.chatSeenAt(userId);
        return seen == null ? Instant.EPOCH : seen;
    }

    private static Long partnerOf(Link link, Long userId) {
        Long initiator = link.getInitiatorUser().getId();
        return initiator.equals(userId) ? link.getPartnerUser().getId() : initiator;
    }

    private String nameOf(Long userId) {
        return users.findById(userId)
                .map(u -> u.getDisplayName() != null && !u.getDisplayName().isBlank() ? u.getDisplayName() : u.getNickname())
                .orElse("Tu pareja");
    }

    private static String dayOf(Instant instant) {
        return DAYS[ZonedDateTime.ofInstant(instant, CDMX).getDayOfWeek().getValue() - 1];
    }

    private static String dayAndTime(Instant instant) {
        ZonedDateTime at = ZonedDateTime.ofInstant(instant, CDMX);
        return DAYS[at.getDayOfWeek().getValue() - 1] + " " + String.format("%02d:%02d", at.getHour(), at.getMinute());
    }
}
