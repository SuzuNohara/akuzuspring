package com.nexus.agent.ai.retrieval;

import java.util.List;

/**
 * Puerto de recuperación de candidatas (nexus-AGENT-00). Es el seam que AGENT-02 consume y simula.
 *
 * <p><b>Reconciliación con el plan:</b> el handoff §4 fija que NOVA entrega esto como <i>interfaz</i>
 * (con doble de prueba); el dev implementa la recuperación real (embeddings + VectorStore) en una
 * clase que la implementa (p. ej. {@code VectorCandidateRetriever}). Devuelve a lo sumo {@code top-k}
 * candidatas, únicas por {@code activityId} y ordenadas por score descendente; lista vacía si no hay
 * aciertos (R27).
 */
public interface CandidateRetriever {

    List<CandidateActivity> retrieve(RetrievalQuery query);
}
