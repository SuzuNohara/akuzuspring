package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.dto.RecommendationItem;
import com.nexus.entity.Activity;
import com.nexus.entity.Recommendation;
import com.nexus.entity.RecommendationRequest;
import com.nexus.model.ReasonSource;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La tarjeta dice la duracion que cabe en el horario elegido, no el promedio del catalogo. */
class RecommendationItemTest {

    private static final Instant START = Instant.parse("2026-10-20T01:00:00Z");

    private static RecommendationItem itemFor(int avgMinutes, Duration slot) {
        Activity activity = Activity.builder().id(1L).title("Escalada en muro").activityType("SPORT")
                .locationScope("CITY").durationMin(60).durationAvg(avgMinutes).costMxnPp(300).build();
        RecommendationRequest request = RecommendationRequest.builder().id(9L)
                .slotStart(START).slotEnd(START.plus(slot)).build();
        return RecommendationService.toItem(Recommendation.builder().id(5L).request(request).activity(activity)
                .reason("frase").reasonSource(ReasonSource.CATALOG).build());
    }

    @Test
    @DisplayName("una actividad de 1 h 30 en un hueco de 1 h dice 60 min y termina con el hueco")
    void clampedToSlot() {
        RecommendationItem item = itemFor(90, Duration.ofHours(1));

        assertThat(item.durationMinutes()).isEqualTo(60);
        assertThat(item.endsAt()).isEqualTo(START.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("si cabe completa, conserva su duracion")
    void fitsWhole() {
        assertThat(itemFor(45, Duration.ofHours(2)).durationMinutes()).isEqualTo(45);
    }
}
