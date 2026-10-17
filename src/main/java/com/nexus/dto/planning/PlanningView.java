package com.nexus.dto.planning;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Estado completo de la planeacion para quien consulta. Nunca dice cuantas opciones quedan, solo
 * si hay mas (regla de interfaz de la usuaria).
 *
 * @param phase INVITED, FILTERS, OPTIONS, PROPOSAL, AGREED, EXPIRED, CLOSED o CANCELLED
 * @param role INVITER o PARTNER
 * @param activity lineas recientes de lo que hizo la pareja (B2)
 * @param adjustingByPartner la pareja pidio ajustar filtros tras agotar las opciones (B7)
 * @param agreed la opcion acordada, solo con AGREED (B5)
 */
public record PlanningView(
        Long id,
        String status,
        String phase,
        String role,
        PlanningMember me,
        PlanningMember partner,
        Instant inviteExpiresAt,
        Instant sessionExpiresAt,
        PlanningFilters filters,
        List<String> activity,
        List<PlanningOption> options,
        boolean hasMore,
        Set<String> relaxedFilters,
        boolean adjustingByPartner,
        ProposalView proposal,
        PlanningOption agreed,
        Long eventId,
        long unreadMessages) {}
