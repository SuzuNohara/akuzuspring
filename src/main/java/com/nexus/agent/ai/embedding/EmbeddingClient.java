package com.nexus.agent.ai.embedding;

import java.time.Duration;
import java.util.List;

/**
 * Puerto de embeddings (nexus-AGENT-00). El dev implementa {@code OllamaEmbeddingClient} (bge-m3,
 * 1024 dim) encima. Devuelve un vector por texto, en el mismo orden de entrada.
 */
public interface EmbeddingClient {

    List<float[]> embed(List<String> texts, Duration timeout);

    int dimension();

    String model();
}
