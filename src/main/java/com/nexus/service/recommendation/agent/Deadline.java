package com.nexus.service.recommendation.agent;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * [t06b-agente-java] U12 - Reloj del presupuesto de una solicitud (RNF-12), con el reloj inyectable
 * para probar con tiempo falso. Solo mide: si se corta la llamada (HARD) o se deja terminar y se
 * registra el exceso (SOFT) lo decide {@link RecommendationComposer}.
 */
public final class Deadline {

    /** Margen que se deja al final para guardar la respuesta. */
    static final long RESERVE_MS = 300;

    private final long budgetMs;
    private final LongSupplier nanos;
    private final long startedNanos;

    private Deadline(long budgetMs, LongSupplier nanos) {
        this.budgetMs = budgetMs;
        this.nanos = nanos;
        this.startedNanos = nanos.getAsLong();
    }

    /**
     * Empieza a contar.
     *
     * @param nanos reloj monotono en nanosegundos ({@code System::nanoTime} en produccion)
     */
    public static Deadline start(long budgetMs, LongSupplier nanos) {
        return new Deadline(budgetMs, nanos);
    }

    /** Milisegundos desde que empezo. */
    public long elapsedMs() {
        return TimeUnit.NANOSECONDS.toMillis(nanos.getAsLong() - startedNanos);
    }

    /** Lo que queda del presupuesto; nunca negativo. */
    public long remainingMs() {
        return Math.max(0, budgetMs - elapsedMs());
    }

    /** Cuanto se paso del presupuesto; 0 mientras no se pase. */
    public long overBudgetMs() {
        return Math.max(0, elapsedMs() - budgetMs);
    }

    /** Si quedan al menos {@code ms}. */
    public boolean hasAtLeast(long ms) {
        return remainingMs() >= ms;
    }

    /** Tope de una llamada: {@code min(maxMs, restante - 300 ms)}, nunca negativo. */
    public Duration callTimeout(long maxMs) {
        return Duration.ofMillis(Math.max(0, Math.min(maxMs, remainingMs() - RESERVE_MS)));
    }
}
