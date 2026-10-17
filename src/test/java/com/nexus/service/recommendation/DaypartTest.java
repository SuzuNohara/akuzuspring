package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Momento del dia de una franja, en hora de la CDMX, y si una actividad lo admite. */
class DaypartTest {

    private static final ZoneId CDMX = ZoneId.of("America/Mexico_City");

    @Test
    @DisplayName("antes de las 12 es manana, antes de las 19 es tarde y despues es noche")
    void ofUsesLocalHour() {
        // 2026-10-17 en CDMX es UTC-6
        assertThat(Daypart.of(Instant.parse("2026-10-17T17:59:00Z"), CDMX)).isEqualTo(Daypart.MORNING);
        assertThat(Daypart.of(Instant.parse("2026-10-17T18:00:00Z"), CDMX)).isEqualTo(Daypart.AFTERNOON);
        assertThat(Daypart.of(Instant.parse("2026-10-18T00:59:00Z"), CDMX)).isEqualTo(Daypart.AFTERNOON);
        assertThat(Daypart.of(Instant.parse("2026-10-18T01:00:00Z"), CDMX)).isEqualTo(Daypart.NIGHT);
    }

    @Test
    @DisplayName("una actividad con ANY o con el mismo momento lo admite; otra no")
    void admits() {
        assertThat(Daypart.NIGHT.admits("ANY")).isTrue();
        assertThat(Daypart.NIGHT.admits("NIGHT")).isTrue();
        assertThat(Daypart.NIGHT.admits("MORNING")).isFalse();
        assertThat(Daypart.MORNING.admits(null)).isTrue();
        assertThat(Daypart.NIGHT.admits("AFTERNOON|NIGHT")).isTrue();
        assertThat(Daypart.MORNING.admits("AFTERNOON|NIGHT")).isFalse();
    }
}
