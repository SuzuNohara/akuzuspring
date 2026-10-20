package com.nexus.service;

import com.nexus.dto.PendingRatingResponse;
import com.nexus.dto.RatingResponse;
import com.nexus.entity.DateRating;
import com.nexus.entity.Event;
import com.nexus.entity.EventStatus;
import com.nexus.entity.Link;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU42 / RF-41 - Calificar la satisfaccion de una cita.
 *
 * <p>Una cita es un evento suelto (no repetido) del vinculo activo, confirmado por los dos y ya
 * terminado. Se califica una sola vez y solo dentro de las 48 h siguientes a que termina (RN-36).
 * El puntaje se guarda cifrado (RNF-11) y nunca sale en una respuesta (RN-37).
 */
@Service
@RequiredArgsConstructor
public class RatingService {

    /** RN-36 - ventana para calificar despues de que termina la cita. */
    static final Duration WINDOW = Duration.ofHours(48);

    static final int MIN_SCORE = 1;
    static final int MAX_SCORE = 5;

    private final LinkRepository links;
    private final EventRepository events;
    private final DateRatingRepository ratings;
    private final AesEncryptionService aes;
    private final Clock clock;

    /**
     * Citas que esta persona aun puede calificar, la mas proxima a vencer primero (Figma C12a).
     *
     * @implNote O(e log e) con e = eventos confirmados del vinculo.
     */
    @Transactional(readOnly = true)
    public List<PendingRatingResponse> pending(Long userId) {
        Optional<Link> link = links.findActiveLinkByUserId(userId);
        if (link.isEmpty()) {
            return List.of();
        }
        Instant now = clock.instant();
        List<Event> open = events.findByLinkIdAndStatus(link.get().getId(), EventStatus.CONFIRMED).stream()
                .filter(e -> isDate(e) && isWithinWindow(e, now))
                .toList();
        if (open.isEmpty()) {
            return List.of();
        }
        Set<Long> rated = new HashSet<>(ratings.findRatedEventIds(userId, open.stream().map(Event::getId).toList()));
        return open.stream()
                .filter(e -> !rated.contains(e.getId()))
                .map(e -> new PendingRatingResponse(
                        e.getId(), e.getTitle(), e.getStartDateTime(), e.getEndDateTime(), e.getLocation(), expiresAt(e)))
                .sorted(Comparator.comparing(PendingRatingResponse::expiresAt))
                .toList();
    }

    /** Guarda la calificacion de esta persona (CU42 flujo principal; FA01 vencida, FA02 ya calificada). */
    @Transactional
    public RatingResponse rate(Long userId, Long eventId, int score) {
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new BadRequestException("Elige una calificación de 1 a 5.");
        }
        Event event = events.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos esa cita."));
        Long activeLinkId = links.findActiveLinkByUserId(userId).map(Link::getId).orElse(null);
        if (!event.getLink().getId().equals(activeLinkId)) {
            throw new ForbiddenException("Solo puedes calificar citas de tu vínculo actual.");
        }
        if (!isDate(event)) {
            throw new BadRequestException("Solo se califican citas confirmadas por los dos.");
        }
        Instant now = clock.instant();
        if (event.getEndDateTime().isAfter(now)) {
            throw new BadRequestException("Podrás calificar esta cita cuando termine.");
        }
        if (!isWithinWindow(event, now)) {
            throw new BadRequestException("Ya no es posible calificar esta cita.");
        }
        if (ratings.existsByEventIdAndUserId(eventId, userId)) {
            throw new ConflictException("Ya calificaste esta cita.");
        }
        DateRating saved = ratings.save(DateRating.builder()
                .eventId(eventId)
                .userId(userId)
                .encryptedScore(aes.encrypt("{\"score\":" + score + "}"))
                .build());
        return new RatingResponse(eventId, saved.getCreatedAt() != null ? saved.getCreatedAt() : now);
    }

    /** Evento suelto, vigente y aprobado por los dos. */
    private static boolean isDate(Event e) {
        return e.getDeletedAt() == null
                && !Boolean.TRUE.equals(e.getIsRecurring())
                && e.getStatus() == EventStatus.CONFIRMED
                && e.getEndDateTime() != null;
    }

    private static boolean isWithinWindow(Event e, Instant now) {
        return !e.getEndDateTime().isAfter(now) && now.isBefore(expiresAt(e));
    }

    private static Instant expiresAt(Event e) {
        return e.getEndDateTime().plus(WINDOW);
    }
}
