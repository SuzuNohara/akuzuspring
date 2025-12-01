package com.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityResponse {
    private Long userId;
    private List<AvailabilitySlot> freeSlots = new ArrayList<>();
    private Instant searchStartDate;
    private Instant searchEndDate;
}
