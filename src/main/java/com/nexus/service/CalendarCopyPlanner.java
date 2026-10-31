package com.nexus.service;

import com.nexus.dto.CopyPlanDTO;
import com.nexus.dto.CopyPlanDTO.CopyItem;
import com.nexus.dto.CopyPlanDTO.CopyRemoval;
import com.nexus.entity.Event;
import com.nexus.entity.EventStatus;
import com.nexus.entity.ExternalCalendar;
import com.nexus.entity.ExternalEvent;
import com.nexus.repository.EventRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.ExternalEventRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [sync-calendario] RF-22 - Decide que hacer en el calendario destino del telefono para que sus
 * copias queden igual que los eventos de Nexus confirmados de la persona (los suyos y los de la
 * pareja). Manda Nexus: lo que cambia o se borra aqui, cambia o se borra en el telefono.
 */
@Service
@RequiredArgsConstructor
public class CalendarCopyPlanner {

    /** Lo que termino antes de esto ya no se copia ni se actualiza (tampoco se borra). */
    static final Duration PAST = Duration.ofDays(30);

    private static final Pattern UNTIL = Pattern.compile("UNTIL=(\\d{8})");

    private final ExternalCalendarRepository calendars;
    private final ExternalEventRepository externalEvents;
    private final EventRepository events;

    /** El plan, o vacio si la persona no eligio calendario destino. */
    @Transactional(readOnly = true)
    public Optional<CopyPlanDTO> planFor(Long userId, Instant now) {
        Optional<ExternalCalendar> found = calendars.findExportTarget(userId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ExternalCalendar target = found.get();
        Map<Long, Event> mine = new LinkedHashMap<>();
        for (Event e : events.findByUserIdAndNotDeleted(userId)) {
            mine.put(e.getId(), e);
        }
        List<Long> calendarIds = calendars.findByUserIdAndIsActiveTrue(userId).stream().map(ExternalCalendar::getId).toList();

        List<CopyItem> update = new ArrayList<>();
        List<CopyItem> keep = new ArrayList<>();
        List<CopyRemoval> remove = new ArrayList<>();
        Set<Long> copied = new HashSet<>();
        for (ExternalEvent copy : externalEvents.findCopies(calendarIds)) {
            Event e = mine.get(copy.getAppEventId());
            boolean inTarget = copy.getExternalCalendarId().equals(target.getId());
            if (e == null || e.getStatus() != EventStatus.CONFIRMED || (inTarget && !copied.add(e.getId()))) {
                remove.add(removalOf(copy));
            } else if (!isCurrent(e, now)) {
                // Ya paso: la copia se queda como esta, en el calendario donde este.
                copied.add(e.getId());
            } else if (!inTarget) {
                remove.add(removalOf(copy));
            } else if (!signatureOf(e).equals(copy.getExportSignature())) {
                update.add(itemOf(e, copy.getDeviceEventId()));
            } else {
                keep.add(itemOf(e, copy.getDeviceEventId()));
            }
        }

        List<CopyItem> create = mine.values().stream()
                .filter(e -> e.getStatus() == EventStatus.CONFIRMED && isCurrent(e, now) && !copied.contains(e.getId()))
                .map(e -> itemOf(e, null))
                .toList();
        return Optional.of(new CopyPlanDTO(target.getId(), target.getDeviceCalendarId(), create, update, remove, keep));
    }

    /** SHA-256 de titulo (sin espacios sobrantes), inicio y fin al minuto, y repeticion. */
    public static String signatureOf(Event e) {
        String text = normalized(e.getTitle()) + "|" + minute(e.getStartDateTime()) + "|"
                + minute(e.getEndDateTime()) + "|" + (e.getRecurrencePattern() == null ? "" : e.getRecurrencePattern());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Termina despues de hace 30 dias, o es una serie que todavia no termina. */
    static boolean isCurrent(Event e, Instant now) {
        Instant since = now.minus(PAST);
        if (Boolean.TRUE.equals(e.getIsRecurring()) && e.getRecurrencePattern() != null) {
            Matcher until = UNTIL.matcher(e.getRecurrencePattern());
            return !until.find() || LocalDate.parse(until.group(1), DateTimeFormatter.BASIC_ISO_DATE)
                    .plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().isAfter(since);
        }
        return e.getEndDateTime() != null && e.getEndDateTime().isAfter(since);
    }

    /** Los de todo el dia se guardan de 00:00 a 23:59 UTC (crear evento en la app). */
    static boolean isAllDay(Event e) {
        ZonedDateTime start = e.getStartDateTime().atZone(ZoneOffset.UTC);
        ZonedDateTime end = e.getEndDateTime().atZone(ZoneOffset.UTC);
        return start.getHour() == 0 && start.getMinute() == 0 && end.getHour() == 23 && end.getMinute() == 59;
    }

    private static CopyItem itemOf(Event e, String deviceEventId) {
        return new CopyItem(e.getId(), deviceEventId, e.getTitle(), e.getStartDateTime(), e.getEndDateTime(),
                isAllDay(e), e.getLocation(), e.getDescription(),
                Boolean.TRUE.equals(e.getIsRecurring()) ? e.getRecurrencePattern() : null);
    }

    private static CopyRemoval removalOf(ExternalEvent copy) {
        return new CopyRemoval(copy.getAppEventId(), copy.getExternalCalendarId(), copy.getDeviceEventId());
    }

    private static String normalized(String title) {
        return title == null ? "" : title.trim().replaceAll("\\s+", " ");
    }

    private static Instant minute(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MINUTES);
    }
}
