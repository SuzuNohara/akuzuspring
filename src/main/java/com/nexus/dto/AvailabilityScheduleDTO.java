package com.nexus.dto;

import com.nexus.entity.AvailabilitySchedule;
import lombok.*;

/**
 * DTO para configuración de horarios permitidos
 * CU28 - Identificar espacios disponibles individuales
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilityScheduleDTO {

    private String day; // MONDAY, TUESDAY, etc.
    private Boolean enabled;
    private String startTime; // HH:mm format
    private String endTime;   // HH:mm format

    public static AvailabilityScheduleDTO fromEntity(AvailabilitySchedule schedule) {
        return AvailabilityScheduleDTO.builder()
                .day(schedule.getDayOfWeek().name())
                .enabled(schedule.getIsEnabled())
                .startTime(schedule.getStartTime().toString())
                .endTime(schedule.getEndTime().toString())
                .build();
    }
}
