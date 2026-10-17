package com.nexus.service.recommendation;

import com.nexus.model.ReasonSource;

/** Frase que acompana una recomendacion y quien la escribio (el agente o el catalogo). */
public record WrittenReason(Long activityId, String reason, ReasonSource source) {}
