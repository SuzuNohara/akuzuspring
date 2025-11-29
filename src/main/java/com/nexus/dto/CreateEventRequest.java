package com.nexus.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventRequest {
    
    @NotBlank(message = "El título del evento es obligatorio")
    @Size(max = 255, message = "El título no puede exceder los 255 caracteres")
    private String title;
    
    @NotNull(message = "La fecha y hora de inicio son obligatorias")
    private String startDateTime; // ISO 8601 string with timezone
    
    @NotNull(message = "La fecha y hora de fin son obligatorias")
    private String endDateTime;   // ISO 8601 string with timezone
    
    @Size(max = 500, message = "La ubicación no puede exceder los 500 caracteres")
    private String location;
    
    @Size(max = 100, message = "La categoría no puede exceder los 100 caracteres")
    private String category;
    
    @Size(max = 2000, message = "La descripción no puede exceder los 2000 caracteres")
    private String description;
    
    // Campos opcionales para futuras funcionalidades
    @Builder.Default
    private Boolean isRecurring = false;
    private String recurrencePattern;
    private String color;
    private Integer reminderMinutes; // Compatibilidad con API anterior
    private List<ReminderDTO> reminders; // Nuevo: múltiples recordatorios
}