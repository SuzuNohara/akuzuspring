package com.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionLogResponse {
    private Long id;
    private Double valence;
    private Double activation;
    private String label;
    private Instant loggedAt;
}
