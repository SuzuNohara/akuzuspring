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
public class PreferenceCategoryDTO {
    private Long id;
    private String name;
    private String description;
    private List<PreferenceDTO> preferences;
}
