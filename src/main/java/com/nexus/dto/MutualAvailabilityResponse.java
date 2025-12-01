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
public class MutualAvailabilityResponse {
    private Long user1Id;
    private Long user2Id;
    private List<AvailabilitySlot> mutualFreeSlots = new ArrayList<>();
    private Instant searchStartDate;
    private Instant searchEndDate;
}
