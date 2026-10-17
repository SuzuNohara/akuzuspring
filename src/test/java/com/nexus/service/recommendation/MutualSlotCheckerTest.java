package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nexus.backend.service.AvailabilityService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** A4 / R13 - La franja pedida debe caber en un hueco libre de los dos (en hora de la CDMX). */
@ExtendWith(MockitoExtension.class)
class MutualSlotCheckerTest {

    @Mock private AvailabilityService availability;
    @InjectMocks private MutualSlotChecker checker;

    @BeforeEach
    void setUp() {
        // Sabado 17-oct-2026 libres los dos de 18:00 a 21:00 (CDMX, UTC-6)
        when(availability.findMutualAvailability(1L, 2L, MutualSlotChecker.DAYS_AHEAD))
                .thenReturn(Map.of("mutualAvailability", List.of(Map.of(
                        "date", "2026-10-17",
                        "mutualFreeSlots", List.of(Map.of("start", "18:00", "end", "21:00"))))));
    }

    @Test
    @DisplayName("una franja dentro del hueco comun es valida")
    void insideFreeSlot() {
        assertThat(checker.isMutualFree(1L, 2L,
                Instant.parse("2026-10-18T00:00:00Z"), Instant.parse("2026-10-18T03:00:00Z"))).isTrue();
        assertThat(checker.isMutualFree(1L, 2L,
                Instant.parse("2026-10-18T01:00:00Z"), Instant.parse("2026-10-18T02:00:00Z"))).isTrue();
    }

    @Test
    @DisplayName("una franja que se sale del hueco o es de otro dia no es valida")
    void outsideFreeSlot() {
        assertThat(checker.isMutualFree(1L, 2L,
                Instant.parse("2026-10-17T23:00:00Z"), Instant.parse("2026-10-18T01:00:00Z"))).isFalse();
        assertThat(checker.isMutualFree(1L, 2L,
                Instant.parse("2026-10-19T00:00:00Z"), Instant.parse("2026-10-19T01:00:00Z"))).isFalse();
    }
}
