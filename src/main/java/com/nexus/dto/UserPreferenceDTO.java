package com.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceDTO {
    private Long preferenceId;
    private String preferenceName;
    private String categoryName;
    private Integer level;
    private String notes;
}
