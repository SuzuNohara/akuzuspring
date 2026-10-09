package com.nexus.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Historial de ánimo (nexus-EMO-01, CU32): resumen de los últimos 7 días y página de registros.
 *
 * <p>{@code today} lo fija el servidor (RN-38): el cliente etiqueta "Hoy/Ayer" respecto a esta
 * fecha, no al reloj del teléfono. {@code nextBefore} en {@code null} indica que no hay más páginas.
 */
public record MoodHistoryResponse(
        LocalDate today,
        List<MoodDaySummary> last7Days,
        List<MoodEntryResponse> entries,
        LocalDate nextBefore) {}
