package com.nexus.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexus.entity.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {
    
    private Long id;
    private String title;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant startDateTime;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant endDateTime;
    private String location;
    private String category;
    private String description;
    private EventStatus status;
    
    // Información del creador
    private Long creatorUserId;
    private String creatorName;
    private String creatorNickname;
    
    // Información del vínculo
    private Long linkId;
    private String partnerName;
    private String partnerNickname;
    
    // Estados de aprobación
    private Boolean creatorApproved;
    private Boolean partnerApproved;
    private Boolean fullyApproved;
    
    // Fechas de aprobación
    private Instant creatorApprovedAt;
    private Instant partnerApprovedAt;
    
    // Campos opcionales
    private Boolean isRecurring;
    private String recurrencePattern;
    private String color;
    private Integer reminderMinutes; // Compatibilidad con API anterior
    private List<ReminderDTO> reminders; // Nuevo: múltiples recordatorios
    
    // Excepciones de eventos recurrentes (fechas de instancias eliminadas/modificadas)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private List<Instant> exceptionDates;
    
    // Metadatos
    private Instant createdAt;
    private Instant updatedAt;
    
    // Usuario que necesita aprobar (si hay pendientes)
    private Long pendingApprovalUserId;
    private String pendingApprovalUserName;
}