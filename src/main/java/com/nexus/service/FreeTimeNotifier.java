package com.nexus.service;

import com.nexus.backend.service.AvailabilityService;
import com.nexus.config.FreeTimeProperties;
import com.nexus.entity.Link;
import com.nexus.repository.LinkRepository;
import com.nexus.service.push.NoticeTypes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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

    private final FreeTimeProperties properties;
    private final LinkRepository links;
    private final AvailabilityService availability;
    private final AppNotificationService notifications;
    private final Clock clock;

    /** Un horario libre de los dos. */
    record FreeSlot(LocalDate date, LocalTime start, LocalTime end) {}

    /** [libres-juntos] Cuantos horarios libres limpios hay y cual se propone. */
    record FreeTimeSummary(int count, FreeTimeProposals.Window best) {}

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
        LocalDateTime now = LocalDateTime.now(clock.withZone(CDMX));
        Optional<FreeTimeSummary> summary = summaryOf(
                availability.findMutualAvailability(a, b, DAYS_AHEAD), properties.minMinutes(), now);
        if (summary.isEmpty()) {
            return;
        }
        // [libres-juntos] La mejor propuesta, como la ve la app, en tono calido.
        FreeTimeProposals.Label label = FreeTimeProposals.label(summary.get().best(), now.toLocalDate());
        String title = "Esta semana tienen tiempo juntos";
        String body = sentenceStart(label.when()) + " tienen " + label.length() + ". ¿Planeamos algo?";
        // [ux-ajustes] D2: no repetir el mismo aviso que ya recibio esa semana.
        Instant weekAgo = clock.instant().minus(Duration.ofDays(7));
        for (Long member : List.of(a, b)) {
            boolean repeated = notifications.lastOf(member, NoticeTypes.FREE_TIME, weekAgo)
                    .filter(last -> title.equals(last.getTitle()) && body.equals(last.getBody()))
                    .isPresent();
            if (!repeated) {
                notifications.notify(member, null, NoticeTypes.FREE_TIME, title, body, Map.of("freeTogether", "1"));
            }
        }
    }

    /**
     * [libres-juntos] Los horarios libres limpios (desde ahora, a :00/:30, partidos en manana, tarde y
     * noche, de al menos {@code minMinutes}) y la propuesta del dia mas cercano. Vacio si no hay
     * ninguno.
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
                .toList();
        List<FreeTimeProposals.Window> windows = FreeTimeProposals.windowsOf(slots, now, true).stream()
                .filter(w -> w.minutes() >= minMinutes)
                .toList();
        List<FreeTimeProposals.Window> proposals = FreeTimeProposals.proposalsOf(windows, 1);
        return proposals.isEmpty() ? Optional.empty() : Optional.of(new FreeTimeSummary(windows.size(), proposals.get(0)));
    }

    /** "Sábado en la tarde" → "El sábado en la tarde"; "Hoy…" y "Mañana…" se quedan igual. */
    private static String sentenceStart(String when) {
        if (when.startsWith("Hoy") || when.startsWith("Mañana")) {
            return when;
        }
        return "El " + Character.toLowerCase(when.charAt(0)) + when.substring(1);
    }
}
