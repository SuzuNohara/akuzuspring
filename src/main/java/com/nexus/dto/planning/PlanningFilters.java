package com.nexus.dto.planning;

import com.nexus.model.Scope;
import com.nexus.model.ZoneType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;

/**
 * B2 - Filtros compartidos de la planeacion. Todo opcional mientras se ajustan; "Listo" exige zona
 * y horario. Sin coordenadas: la ubicacion viaja solo en {@link ReadyRequest} (RN-30).
 *
 * @param categories codigos de {@code IdeaCategory}; vacio = cualquiera
 */
public record PlanningFilters(
        ZoneType zoneType,
        String boroughCode,
        Scope scope,
        @Min(0) @Max(15_000) Integer maxCostMxn,
        List<String> categories,
        Instant slotStart,
        Instant slotEnd) {}
