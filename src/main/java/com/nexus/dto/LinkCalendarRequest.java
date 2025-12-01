package com.nexus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkCalendarRequest {
    @NotBlank(message = "Device calendar ID is required")
    private String deviceCalendarId;

    @NotBlank(message = "Calendar name is required")
    private String calendarName;

    private String calendarSource;
    private String calendarColor;

    @NotNull(message = "Sync enabled status is required")
    private Boolean syncEnabled = true;

    @NotNull(message = "Privacy mode is required")
    private String privacyMode = "BUSY_ONLY"; // FULL_DETAILS o BUSY_ONLY
}
