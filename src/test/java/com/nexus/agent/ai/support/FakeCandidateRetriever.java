package com.nexus.agent.ai.support;

import com.nexus.agent.ai.retrieval.CandidateActivity;
import com.nexus.agent.ai.retrieval.CandidateRetriever;
import com.nexus.agent.ai.retrieval.RetrievalQuery;
import java.util.List;

/**
 * Doble de {@link CandidateRetriever} para AGENT-02 (nexus-AGENT-00, §4): devuelve una lista fija de
 * candidatas y recuerda la última consulta recibida.
 */
public final class FakeCandidateRetriever implements CandidateRetriever {

    private final List<CandidateActivity> candidates;
    private RetrievalQuery lastQuery;

    public FakeCandidateRetriever(List<CandidateActivity> candidates) {
        this.candidates = List.copyOf(candidates);
    }

    public RetrievalQuery lastQuery() {
        return lastQuery;
    }

    @Override
    public List<CandidateActivity> retrieve(RetrievalQuery query) {
        this.lastQuery = query;
        return candidates;
    }
}
