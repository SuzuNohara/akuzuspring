package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexus.config.AgentProperties;
import com.nexus.exception.AgentUnavailableException;
import com.nexus.integration.AgentTestProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [T-06] U5 - I4: al arrancar se reindexa si procede; si el agente esta caido solo se avisa. */
class ActivityIndexerRunnerTest {

    private final ActivityIndexer indexer = mock(ActivityIndexer.class);

    private static AgentProperties withAgent(boolean enabled, boolean indexOnStartup) {
        AgentProperties d = AgentTestProperties.defaults();
        return new AgentProperties(
                d.ollamaUrl(), d.chatModel(), d.embedModel(), d.qdrantUrl(), d.collection(),
                d.llmTimeout(), d.candidatePool(), indexOnStartup, enabled);
    }

    @Test
    @DisplayName("I4: si Qdrant u Ollama estan caidos no tumba el arranque")
    void warnsWhenAgentIsDown() {
        when(indexer.ensureIndexed()).thenThrow(new AgentUnavailableException("caido"));
        ActivityIndexerRunner runner = new ActivityIndexerRunner(indexer, withAgent(true, true));

        assertThatCode(runner::indexNow).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("con el agente apagado no toca Qdrant")
    void doesNothingWhenAgentIsOff() {
        new ActivityIndexerRunner(indexer, withAgent(false, true)).run(null);
        new ActivityIndexerRunner(indexer, withAgent(true, false)).run(null);

        verify(indexer, never()).ensureIndexed();
    }
}
