package com.nexus.dto;

import com.nexus.entity.ExternalCalendar;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalCalendarDTO {
    private Long id;
    private String deviceCalendarId;
    private String calendarName;
    private String calendarSource;
    private String calendarColor;
    private Boolean syncEnabled;
    private String privacyMode;
    private Instant lastSync;
    private Boolean isActive;

    public static ExternalCalendarDTO fromEntity(ExternalCalendar calendar) {
        ExternalCalendarDTO dto = new ExternalCalendarDTO();
        dto.setId(calendar.getId());
        dto.setDeviceCalendarId(calendar.getDeviceCalendarId());
        dto.setCalendarName(calendar.getCalendarName());
        dto.setCalendarSource(calendar.getSource().name());
        dto.setCalendarColor(calendar.getCalendarColor());
        dto.setSyncEnabled(calendar.getSyncEnabled());
        dto.setPrivacyMode(calendar.getPrivacyMode().name());
        dto.setLastSync(calendar.getLastSync());
        dto.setIsActive(calendar.getIsActive());
        return dto;
    }
}
