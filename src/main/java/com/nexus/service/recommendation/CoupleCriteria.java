package com.nexus.service.recommendation;

import com.nexus.entity.Borough;
import com.nexus.model.IdeaCategory;
import com.nexus.model.Scope;
import com.nexus.model.ZoneType;
import com.nexus.service.ProximityFilter.Coordinates;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * RF-38 - Filtros que eligieron los dos en una planeacion en pareja, ya validados y resueltos.
 *
 * @param borough solo con {@link ZoneType#BOROUGH}
 * @param references puntos para medir la distancia (ubicaciones de los dos o centro de la alcaldia);
 *     nunca se guardan (RN-30)
 */
public record CoupleCriteria(
        ZoneType zoneType,
        Borough borough,
        Scope scope,
        int maxCostMxn,
        Set<IdeaCategory> categories,
        Instant slotStart,
        Instant slotEnd,
        List<Coordinates> references) {}
