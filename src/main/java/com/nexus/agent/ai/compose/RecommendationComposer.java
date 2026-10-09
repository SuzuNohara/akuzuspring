package com.nexus.agent.ai.compose;

import com.nexus.agent.ai.retrieval.CandidateActivity;
import com.nexus.agent.ai.retrieval.RetrievalQuery;
import java.util.List;

/**
 * Puerto de composición de la recomendación (nexus-AGENT-00). Es el seam que AGENT-02 consume y
 * simula.
 *
 * <p><b>Reconciliación con el plan:</b> el handoff §4 fija que NOVA entrega esto como <i>interfaz</i>
 * (con doble de prueba); el dev implementa la composición real (LLM + validación + reglas + guarda)
 * en una clase que la implementa (p. ej. {@code LlmRecommendationComposer}). La composición siempre
 * devuelve ≤ 5 drafts ⊆ candidatas y una traza; ante fallo del LLM cae a reglas, nunca lanza (D-08).
 */
public interface RecommendationComposer {

    Composition compose(RetrievalQuery query, List<CandidateActivity> candidates);
}
