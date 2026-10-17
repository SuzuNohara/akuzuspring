package com.nexus.service.recommendation;

import com.nexus.backend.service.AvailabilityService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * RN-27 / R13 - Comprueba en el servidor que la franja pedida sigue libre para los dos, con el mismo
 * calculo de disponibilidad mutua que ve la app ({@link AvailabilityService}, dias y horas CDMX).
 */
@Component
@RequiredArgsConstructor
public class MutualSlotChecker {

    /** Hasta donde se puede pedir una recomendacion. */
    static final int DAYS_AHEAD = 14;

    private final AvailabilityService availability;

    /**
     * Si {@code [start, end]} cabe completa en un hueco libre de los dos, el mismo dia.
     *
     * @implNote O(d * s) con d dias y s huecos por dia en la ventana de 14 dias.
     */
    public boolean isMutualFree(Long user1, Long user2, Instant start, Instant end) {
        ZonedDateTime from = start.atZone(CatalogCandidateSource.CDMX);
        ZonedDateTime to = end.atZone(CatalogCandidateSource.CDMX);
        if (!from.toLocalDate().equals(to.toLocalDate())) {
            return false;
        }
        return slotsOn(availability.findMutualAvailability(user1, user2, DAYS_AHEAD), from.toLocalDate())
                .stream()
                .anyMatch(slot -> fits(slot, from.toLocalTime(), to.toLocalTime()));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> slotsOn(Map<String, Object> mutual, LocalDate day) {
        List<Map<String, Object>> days = (List<Map<String, Object>>) mutual.getOrDefault("mutualAvailability", List.of());
        return days.stream()
                .filter(d -> day.toString().equals(d.get("date")))
                .flatMap(d -> ((List<Map<String, String>>) d.get("mutualFreeSlots")).stream())
                .toList();
    }

    private static boolean fits(Map<String, String> slot, LocalTime start, LocalTime end) {
        LocalTime freeFrom = LocalTime.parse(slot.get("start"));
        LocalTime freeTo = LocalTime.parse(slot.get("end"));
        return !start.isBefore(freeFrom) && !end.isAfter(freeTo);
    }
}
