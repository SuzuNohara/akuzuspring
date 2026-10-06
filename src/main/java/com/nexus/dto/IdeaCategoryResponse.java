package com.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** RF-34 - Categoria del banco de ideas para los filtros de la app. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdeaCategoryResponse {
    private String code;
    private String label;
}
