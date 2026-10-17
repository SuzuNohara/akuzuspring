package com.nexus.dto.planning;

/** Una de las dos personas de la planeacion, desde el punto de vista de quien consulta. */
public record PlanningMember(Long userId, String name, boolean online, boolean ready) {}
