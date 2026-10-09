package com.nexus.agent.ai.support;

import com.nexus.agent.ai.embedding.EmbeddingClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Doble de {@link EmbeddingClient} para AGENT-02 y los tests del motor (nexus-AGENT-00, §4). Vector
 * determinista por hash del texto: el mismo texto da siempre el mismo vector y textos iguales son
 * idénticos. {@link #failNext(RuntimeException)} fuerza un fallo en la siguiente llamada.
 */
public final class FakeEmbeddingClient implements EmbeddingClient {

    private final int dim;
    private int calls;
    private int textsEmbedded;
    private RuntimeException nextFailure;

    public FakeEmbeddingClient(int dim) {
        this.dim = dim;
    }

    public int calls() {
        return calls;
    }

    public int textsEmbedded() {
        return textsEmbedded;
    }

    public void failNext(RuntimeException e) {
        this.nextFailure = e;
    }

    @Override
    public List<float[]> embed(List<String> texts, Duration timeout) {
        calls++;
        if (nextFailure != null) {
            RuntimeException e = nextFailure;
            nextFailure = null;
            throw e;
        }
        List<float[]> out = new ArrayList<>(texts.size());
        for (String text : texts) {
            textsEmbedded++;
            out.add(vectorFor(text));
        }
        return out;
    }

    @Override
    public int dimension() {
        return dim;
    }

    @Override
    public String model() {
        return "fake-embed";
    }

    private float[] vectorFor(String text) {
        float[] v = new float[dim];
        long seed = text == null ? 0L : text.hashCode() & 0xffffffffL;
        double norm = 0;
        for (int i = 0; i < dim; i++) {
            // Generador lineal congruente determinista por (seed, i).
            seed = (seed * 6364136223846793005L + 1442695040888963407L);
            double x = ((seed >>> 11) / (double) (1L << 53)) * 2 - 1; // en [-1, 1)
            v[i] = (float) x;
            norm += x * x;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) {
                v[i] = (float) (v[i] / norm);
            }
        }
        return v;
    }
}
