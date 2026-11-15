package com.nexus.service;

import com.nexus.backend.service.RecurrenceExpander;
import com.nexus.entity.Event;
import com.nexus.entity.EventStatus;
import com.nexus.entity.ExternalCalendar;
import com.nexus.entity.ExternalEvent;
import com.nexus.repository.EventExceptionRepository;
import com.nexus.repository.EventRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.ExternalEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [eventos-sin-choques] Cuándo está ocupada una persona: sus planes de Nexus confirmados o pendientes
 * (los que se repiten, en cada repetición) y los eventos con hora de los calendarios de su teléfono.
 * No cuentan los de todo el día (como en «libres juntos») ni las copias de planes de Nexus en el
 * teléfono, que ya cuentan como plan de Nexus.
 */
@Service
@RequiredArgsConstructor
public class BusyTimeService {

    private static final ZoneId MEXICO_ZONE = ZoneId.of("America/Mexico_City");
    private static final Set<EventStatus> BUSY_STATUSES = Set.of(EventStatus.CONFIRMED, EventStatus.PENDING);

    private final EventRepository eventRepository;
    private final EventExceptionRepository eventExceptionRepository;
    private final ExternalCalendarRepository externalCalendarRepository;
    private final ExternalEventRepository externalEventRepository;

    /** Un tramo ocupado. Dos tramos que solo se tocan (uno termina cuando empieza el otro) no chocan. */
    public record Interval(Instant start, Instant end) {
        public boolean overlaps(Instant otherStart, Instant otherEnd) {
            return start.isBefore(otherEnd) && otherStart.isBefore(end);
        }
    }

    /**
     * Tramos ocupados de {@code userId} que tocan {@code [from, to]}.
     *
     * @param ignoreEventId plan de Nexus que no cuenta (el que se está editando); puede ser null
     */
    @Transactional(readOnly = true)
    public List<Interval> busyIntervals(Long userId, Instant from, Instant to, Long ignoreEventId) {
        List<Interval> busy = new ArrayList<>();

        for (Event event : eventRepository.findByUserIdAndDateRange(userId, from, to)) {
            if (Boolean.TRUE.equals(event.getIsRecurring()) || !counts(event, ignoreEventId)) continue;
            busy.add(new Interval(event.getStartDateTime(), event.getEndDateTime()));
        }
        for (Event recurring : eventRepository.findActiveRecurringByUserIdStartingBefore(userId, to)) {
            if (!counts(recurring, ignoreEventId)) continue;
            busy.addAll(repetitionsOf(recurring, from, to));
        }

        List<Long> calendarIds = externalCalendarRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .map(ExternalCalendar::getId)
                .collect(Collectors.toList());
        if (!calendarIds.isEmpty()) {
            for (ExternalEvent event : externalEventRepository.findEventsByCalendarsAndDateRange(calendarIds, from, to)) {
                if (Boolean.TRUE.equals(event.getIsAllDay()) || event.getAppEventId() != null) continue;
                busy.addAll(repetitionsOf(event, from, to));
            }
        }
        return busy;
    }

    private static boolean counts(Event event, Long ignoreEventId) {
        return BUSY_STATUSES.contains(event.getStatus()) && !Objects.equals(event.getId(), ignoreEventId);
    }

    private List<Interval> repetitionsOf(Event base, Instant from, Instant to) {
        Set<LocalDate> skipped = eventExceptionRepository.findExceptionDatesByEventId(base.getId()).stream()
                .map(d -> d.atZone(ZoneOffset.UTC).toLocalDate())
                .collect(Collectors.toSet());
        Duration length = Duration.between(base.getStartDateTime(), base.getEndDateTime());
        List<Interval> result = new ArrayList<>();
        for (Instant start : RecurrenceExpander.occurrences(base.getStartDateTime(), toRrule(base.getRecurrencePattern()),
                null, null, from.minus(length), to, MEXICO_ZONE)) {
            if (skipped.contains(LocalDate.ofInstant(start, MEXICO_ZONE))) continue;
            result.add(new Interval(start, start.plus(length)));
        }
        return result;
    }

    private static List<Interval> repetitionsOf(ExternalEvent event, Instant from, Instant to) {
        Duration length = Duration.between(event.getStartDatetime(), event.getEndDatetime());
        if (event.getRecurrenceRule() == null || event.getRecurrenceRule().isBlank()) {
            return List.of(new Interval(event.getStartDatetime(), event.getEndDatetime()));
        }
        Instant dtstart = event.getRruleDtstartUtc() != null ? event.getRruleDtstartUtc() : event.getStartDatetime();
        try {
            return RecurrenceExpander.occurrences(dtstart, event.getRecurrenceRule(), event.getRruleUntilUtc(),
                            event.getRruleCount(), from.minus(length), to, MEXICO_ZONE)
                    .stream()
                    .map(start -> new Interval(start, start.plus(length)))
                    .collect(Collectors.toList());
        } catch (RuntimeException badRule) {
            // Igual que en libres juntos: si la regla no se entiende, cuenta el evento base.
            return List.of(new Interval(event.getStartDatetime(), event.getEndDatetime()));
        }
    }

    /** La app guarda "DAILY"/"WEEKLY"/"MONTHLY" o una RRULE completa. */
    static String toRrule(String pattern) {
        if (pattern == null || pattern.isBlank()) return "FREQ=DAILY";
        return pattern.contains("FREQ=") ? pattern : "FREQ=" + pattern.trim().toUpperCase();
    }
}
