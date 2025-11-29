package com.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventResponse {
    
    private boolean success;
    private String message;
    private EventResponse event;
    private String partnerNotificationStatus;
}