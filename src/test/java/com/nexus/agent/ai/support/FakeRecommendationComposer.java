package com.nexus.agent.ai.support;

import com.nexus.agent.ai.compose.Composition;
import com.nexus.agent.ai.compose.CompositionMode;
import com.nexus.agent.ai.compose.CompositionTrace;
import com.nexus.agent.ai.compose.RecommendationComposer;
import com.nexus.agent.ai.compose.RecommendationDraft;
import com.nexus.agent.ai.retrieval.CandidateActivity;
import com.nexus.agent.ai.retrieval.RetrievalQuery;
import java.util.ArrayList;
import java.util.List;

/**
 * Doble de {@link RecommendationComposer} para AGENT-02 (nexus-AGENT-00, §4). Por defecto compone un
 * draft por candidata (hasta {@code maxResults}) con una razón fija, en modo {@link
 * CompositionMode#LLM}; o devuelve una {@link Composition} prefijada. Recuerda la última llamada.
 */
public final class FakeRecommendationComposer implements RecommendationComposer {

    private final int maxResults;
    private final Composition scripted;
    private RetrievalQuery lastQuery;
    private List<CandidateActivity> lastCandidates;

    public FakeRecommendationComposer(int maxResults) {
        this.maxResults = maxResults;
        this.scripted = null;
    }

    public FakeRecommendationComposer(Composition scripted) {
        this.maxResults = 0;
        this.scripted = scripted;
    }

    public RetrievalQuery lastQuery() {
        return lastQuery;
    }

    public List<CandidateActivity> lastCandidates() {
        return lastCandidates;
    }

    @Override
    public Composition compose(RetrievalQuery query, List<CandidateActivity> candidates) {
        this.lastQuery = query;
        this.lastCandidates = List.copyOf(candidates);
        if (scripted != null) {
            return scripted;
        }
        List<RecommendationDraft> drafts = new ArrayList<>();
        for (CandidateActivity c : candidates) {
            if (drafts.size() >= maxResults) {
                break;
            }
            drafts.add(new RecommendationDraft(c.activityId(), c.placeId(), c.title(), "Encaja con sus gustos."));
        }
        CompositionTrace trace =
                new CompositionTrace(CompositionMode.LLM, 1, "v1", "fake-llm", 0, 0, 0, 0, 0);
        return new Composition(drafts, trace);
    }
}
