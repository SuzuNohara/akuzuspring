package com.nexus.agent.ai.support;

import com.nexus.agent.ai.llm.LlmClient;
import com.nexus.agent.ai.llm.LlmRequest;
import com.nexus.agent.ai.llm.LlmResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Doble de {@link LlmClient} guionizado para AGENT-02 y los tests del motor (nexus-AGENT-00, §4).
 *
 * <p>Cada elemento del guion es: un {@link String} (contenido a devolver), una {@link
 * RuntimeException} (a lanzar) o una {@link Duration} (tiempo a "consumir" avanzando el reloj falso
 * antes del siguiente elemento). {@link #received()} expone las peticiones recibidas, en orden.
 */
public final class FakeLlmClient implements LlmClient {

    private final String model;
    private final AtomicLong nanoClock;
    private final Deque<Object> script;
    private final List<LlmRequest> received = new ArrayList<>();

    public FakeLlmClient(List<Object> script) {
        this("fake-llm", new AtomicLong(0), script);
    }

    public FakeLlmClient(String model, AtomicLong nanoClock, List<Object> script) {
        this.model = model;
        this.nanoClock = nanoClock;
        this.script = new ArrayDeque<>(script);
    }

    public List<LlmRequest> received() {
        return List.copyOf(received);
    }

    @Override
    public LlmResponse chat(LlmRequest request, Duration timeout) {
        received.add(request);
        while (true) {
            Object next = script.poll();
            if (next == null) {
                throw new IllegalStateException("FakeLlmClient: guion agotado");
            }
            if (next instanceof Duration d) {
                nanoClock.addAndGet(d.toNanos());
                continue;
            }
            if (next instanceof RuntimeException e) {
                throw e;
            }
            String content = (String) next;
            int evalTokens = content.isBlank() ? 0 : content.trim().split("\\s+").length;
            return new LlmResponse(content, 32, evalTokens, 0L, 0L);
        }
    }

    @Override
    public String model() {
        return model;
    }
}
