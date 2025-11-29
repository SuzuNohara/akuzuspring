package com.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEventRequest {
    private String title;
    private String startDateTime; // ISO 8601 string
    private String endDateTime;   // ISO 8601 string
    private String location;
    private String category;
    private String description;
    private Boolean isRecurring;
    private String recurrencePattern;
    private Integer reminderMinutes; // Compatibilidad con API anterior
    private List<ReminderDTO> reminders; // Nuevo: múltiples recordatorios
    private String color;
}
