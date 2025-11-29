package com.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireStatusDTO {
    private boolean completed;
    private int totalPreferences;
    private int completedPreferences;
    private List<UserPreferenceDTO> userPreferences;
}
