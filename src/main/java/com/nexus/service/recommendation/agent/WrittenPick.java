package com.nexus.service.recommendation.agent;

import com.nexus.model.ReasonSource;
import com.nexus.service.recommendation.Candidate;

/**
 * [t06b-agente-java] U11 - Una recomendacion ya con su frase.
 *
 * @param ref numero de la candidata en la lista que vio Mistral (1..n); 0 si la eligieron las reglas
 * @param source {@code AGENT} si la frase es de Mistral, {@code CATALOG} si es la del catalogo
 */
public record WrittenPick(Candidate candidate, int ref, String reason, ReasonSource source) {}
