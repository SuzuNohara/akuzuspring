package com.nexus.dto;

import com.nexus.entity.ExternalEvent;
import com.nexus.entity.ExternalCalendar;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalEventDTO {
    private Long id;
    private Long externalCalendarId;
    private Long ownerId; // ID del usuario dueño del calendario
    private String deviceEventId;
    private String title;
    private Instant startDatetime;
    private Instant endDatetime;
    private String location;
    private String description;
    private Boolean isAllDay;
    private String recurrenceRule;
    private Instant rruleDtstartUtc;
    private Instant rruleUntilUtc;
    private Integer rruleCount;
    private String visibility;

    public static ExternalEventDTO fromEntity(ExternalEvent event, Long ownerId) {
        ExternalEventDTO dto = new ExternalEventDTO();
        dto.setId(event.getId());
        dto.setExternalCalendarId(event.getExternalCalendarId());
        dto.setOwnerId(ownerId);
        dto.setDeviceEventId(event.getDeviceEventId());
        dto.setTitle(event.getTitle());
        dto.setStartDatetime(event.getStartDatetime());
        dto.setEndDatetime(event.getEndDatetime());
        dto.setLocation(event.getLocation());
        dto.setDescription(event.getDescription());
        dto.setIsAllDay(event.getIsAllDay());
        dto.setRecurrenceRule(event.getRecurrenceRule());
        dto.setRruleDtstartUtc(event.getRruleDtstartUtc());
        dto.setRruleUntilUtc(event.getRruleUntilUtc());
        dto.setRruleCount(event.getRruleCount());
        dto.setVisibility(event.getVisibility().name());
        return dto;
    }

    // Aplicar privacidad (RF-23)
    public static ExternalEventDTO fromEntityWithPrivacy(
        ExternalEvent event,
        Long ownerId,
        ExternalCalendar.PrivacyMode privacyMode
    ) {
        ExternalEventDTO dto = fromEntity(event, ownerId);
        
        if (privacyMode == ExternalCalendar.PrivacyMode.BUSY_ONLY) {
            dto.setTitle("Ocupado");
            dto.setLocation(null);
            dto.setDescription(null);
        }
        
        return dto;
    }
}
