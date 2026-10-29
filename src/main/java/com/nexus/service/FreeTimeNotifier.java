package com.nexus.service;

import com.nexus.backend.service.AvailabilityService;
import com.nexus.config.FreeTimeProperties;
import com.nexus.entity.Link;
import com.nexus.repository.LinkRepository;
import com.nexus.service.push.NoticeTypes;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * [avisos-calendario] RF-29 - Cada lunes a las 9:00 (CDMX) le avisa a cada pareja cuantos horarios
 * libres juntos tiene esa semana y cual es el mas cercano. Usa el mismo calculo que la app muestra en
 * "Libres juntos"; si no hay ninguno de al menos {@code minMinutes}, no se avisa nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FreeTimeNotifier {

    static final int DAYS_AHEAD = 7;
    private static final ZoneId CDMX = ZoneId.of("America/Mexico_City");
    private static final String[] WEEKDAYS = {"lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};

    private final FreeTimeProperties properties;
    private final LinkRepository links;
    private final AvailabilityService availability;
    private final AppNotificationService notifications;
    private final Clock clock;

    /** Un horario libre de los dos. */
    record FreeSlot(LocalDate date, LocalTime start, LocalTime end) {}

    /** Cuantos horarios libres hay y cual es el mas cercano. */
    record FreeTimeSummary(int count, FreeSlot first) {}

    @Scheduled(cron = "${nexus.free-time.cron:0 0 9 * * MON}", zone = "America/Mexico_City")
    public void weekly() {
        if (!properties.enabled()) {
            return;
        }
        for (Link link : links.findAllActive()) {
            try {
                notifyCouple(link);
            } catch (RuntimeException e) {
                // Una pareja con error no deja sin aviso a las demas (R6).
                log.warn("Aviso de libres juntos fallo para el vinculo {}: {}", link.getId(), e.getMessage());
            }
        }
    }

    void notifyCouple(Link link) {
        Long a = link.getInitiatorUser().getId();
        Long b = link.getPartnerUser().getId();
        Optional<FreeTimeSummary> summary = summaryOf(
                availability.findMutualAvailability(a, b, DAYS_AHEAD), properties.minMinutes(), LocalDateTime.now(clock.withZone(CDMX)));
        if (summary.isEmpty()) {
            return;
        }
        int n = summary.get().count();
        String title = n == 1 ? "Tienen 1 horario libre juntos" : "Tienen " + n + " horarios libres juntos";
        FreeSlot first = summary.get().first();
        String body = "El más cercano: " + WEEKDAYS[first.date().getDayOfWeek().getValue() - 1]
                + " de " + first.start() + " a " + first.end() + ".";
        for (Long member : List.of(a, b)) {
            notifications.notify(member, null, NoticeTypes.FREE_TIME, title, body, Map.of("freeTogether", "1"));
        }
    }

    /**
     * Horarios libres de al menos {@code minMinutes} que todavia no terminan; el primero es el mas
     * cercano. Vacio si no hay ninguno.
     *
     * @implNote O(s log s) con s horarios libres de la semana.
     */
    @SuppressWarnings("unchecked")
    static Optional<FreeTimeSummary> summaryOf(Map<String, Object> mutual, int minMinutes, LocalDateTime now) {
        List<Map<String, Object>> days = (List<Map<String, Object>>) mutual.getOrDefault("mutualAvailability", List.of());
        List<FreeSlot> slots = days.stream()
                .flatMap(d -> ((List<Map<String, String>>) d.get("mutualFreeSlots")).stream()
                        .map(s -> new FreeSlot(LocalDate.parse((String) d.get("date")),
                                LocalTime.parse(s.get("start")), LocalTime.parse(s.get("end")))))
                .filter(s -> ChronoUnit.MINUTES.between(s.start(), s.end()) >= minMinutes)
                .filter(s -> s.date().atTime(s.end()).isAfter(now))
                .sorted(Comparator.comparing(FreeSlot::date).thenComparing(FreeSlot::start))
                .toList();
        return slots.isEmpty() ? Optional.empty() : Optional.of(new FreeTimeSummary(slots.size(), slots.get(0)));
    }
}
