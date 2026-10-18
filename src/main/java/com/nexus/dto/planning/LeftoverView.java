package com.nexus.dto.planning;

/**
 * La opcion que quedo al cerrarse la planeacion sin acuerdo.
 *
 * @param kind MY_PROPOSAL (la propuse y no respondio), THEIR_PROPOSAL (no alcance a responder) o MATCH
 *     (coincidimos y nadie la propuso)
 */
public record LeftoverView(PlanningOption option, String kind) {}
