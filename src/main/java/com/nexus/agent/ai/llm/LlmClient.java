package com.nexus.agent.ai.llm;

import java.time.Duration;

/**
 * Puerto del modelo de lenguaje (nexus-AGENT-00). Contrato estable: la app y AGENT-02 dependen de
 * esta interfaz; el dev implementa {@code OllamaLlmClient} (y, más adelante, {@code
 * BedrockLlmClient}) encima. Los dobles de prueba viven en {@code src/test}.
 */
public interface LlmClient {

    LlmResponse chat(LlmRequest request, Duration timeout);

    String model();
}
