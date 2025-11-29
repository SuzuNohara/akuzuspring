package com.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceRequest {
    private Long preferenceId;
    private Integer level;  // 0-100 o null
    private String notes;
}
