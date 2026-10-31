package com.nexus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncEventRequest {
    @NotNull(message = "External calendar ID is required")
    private Long externalCalendarId;

    @NotBlank(message = "Device event ID is required")
    private String deviceEventId;

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Start datetime is required")
    private Instant startDatetime;

    @NotNull(message = "End datetime is required")
    private Instant endDatetime;

    private String location;
    private String description;
    private Boolean isAllDay = false;
    private String recurrenceRule;
    private Instant rruleDtstartUtc; // Fecha de inicio de la recurrencia
    private Instant rruleUntilUtc;   // Fecha de fin de la recurrencia
    private Integer rruleCount;      // Número de ocurrencias
    private Instant lastDeviceUpdate; // Para detectar conflictos (RF-24)
    /** [avisos-calendario] Evento de Nexus del que salio la copia; solo al copiarlo desde la app. */
    private Long appEventId;
    /**
     * [sync-calendario] RF-22: la escribio Nexus en el telefono (no la cambio la persona); se guarda
     * con su firma y nunca es conflicto.
     */
    private Boolean fromNexus = false;
}
