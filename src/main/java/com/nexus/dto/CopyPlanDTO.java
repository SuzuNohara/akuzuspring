package com.nexus.dto;

import java.time.Instant;
import java.util.List;

/**
 * [sync-calendario] RF-22 - Lo que la app tiene que hacer en el calendario destino del telefono
 * para que las copias queden igual que los eventos de Nexus confirmados.
 */
public record CopyPlanDTO(
        Long externalCalendarId,
        String deviceCalendarId,
        List<CopyItem> create,
        List<CopyItem> update,
        List<CopyRemoval> remove) {

    /** Un evento de Nexus a copiar; {@code deviceEventId} es {@code null} al crear. */
    public record CopyItem(
            Long appEventId,
            String deviceEventId,
            String title,
            Instant start,
            Instant end,
            boolean allDay,
            String location,
            String description,
            String recurrencePattern) {}

    /** Una copia a quitar del telefono. */
    public record CopyRemoval(Long appEventId, Long externalCalendarId, String deviceEventId) {}
}
