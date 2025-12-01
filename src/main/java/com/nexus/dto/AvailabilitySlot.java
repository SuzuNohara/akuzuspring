package com.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilitySlot {
    private Instant start;
    private Instant end;
    private Long durationMinutes;

    public AvailabilitySlot(Instant start, Instant end) {
        this.start = start;
        this.end = end;
        this.durationMinutes = (end.toEpochMilli() - start.toEpochMilli()) / (1000 * 60);
    }
}
