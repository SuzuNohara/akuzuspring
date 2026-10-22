package com.nexus.service.recommendation.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [t06b-agente-java] U12.1 - Reloj del presupuesto con tiempo falso. */
class DeadlineTest {

    private final AtomicLong clock = new AtomicLong(1_000_000L);

    private void advanceMs(long ms) {
        clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(ms));
    }

    @Test
    @DisplayName("U12.1: should report remaining and over budget time")
    void shouldReportRemainingAndOverBudgetTime() {
        Deadline deadline = Deadline.start(9_000, clock::get);

        advanceMs(2_000);
        assertThat(deadline.remainingMs()).isEqualTo(7_000);
        assertThat(deadline.overBudgetMs()).isZero();
        assertThat(deadline.hasAtLeast(7_000)).isTrue();
        assertThat(deadline.hasAtLeast(7_001)).isFalse();

        advanceMs(10_000);
        assertThat(deadline.remainingMs()).isZero();
        assertThat(deadline.overBudgetMs()).isEqualTo(3_000);
        assertThat(deadline.elapsedMs()).isEqualTo(12_000);
    }

    @Test
    @DisplayName("U12.1: should cap each call at min(max, remaining - 300), never negative")
    void shouldCapEachCallAtMinOfMaxAndRemainingMinusReserve() {
        Deadline deadline = Deadline.start(9_000, clock::get);

        assertThat(deadline.callTimeout(7_000)).isEqualTo(Duration.ofMillis(7_000));
        advanceMs(4_000);
        assertThat(deadline.callTimeout(7_000)).isEqualTo(Duration.ofMillis(4_700));
        advanceMs(4_900);
        assertThat(deadline.callTimeout(7_000)).isEqualTo(Duration.ZERO);
    }
}
