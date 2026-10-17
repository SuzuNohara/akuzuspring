package com.nexus.service.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.config.AgentProperties;
import com.nexus.integration.ollama.OllamaClient;
import com.nexus.model.ReasonSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * [T-06] U8 - Frases de Mistral para las primeras opciones que se muestran. Cada frase se valida
 * ({@link ReasonValidator}); si una no pasa, si el modelo inventa ids, responde mal o excede
 * {@code llm-timeout}, esa frase sale del catalogo (CU40 FA02) y la respuesta nunca pasa de RNF-12.
 */
@Slf4j
@Primary
@Service
public class MistralReasonWriter implements ReasonWriter {

    /**
     * Cuantas frases redacta el modelo por solicitud: las 5 que se ven primero. Las demas de las 15
     * guardadas (prefetch) usan el catalogo, para no multiplicar el tiempo de Mistral.
     */
    static final int AGENT_BATCH = 5;

    private final OllamaClient ollama;
    private final CatalogReasonWriter catalog;
    private final AgentProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public MistralReasonWriter(OllamaClient ollama, CatalogReasonWriter catalog, AgentProperties properties) {
        this.ollama = ollama;
        this.catalog = catalog;
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Una sola llamada a Ollama, acotada por {@code llm-timeout}; O(n) para combinar.
     */
    @Override
    public List<WrittenReason> write(List<Candidate> candidates, CoupleContext context) {
        List<WrittenReason> fallback = catalog.write(candidates, context);
        if (!properties.enabled() || candidates.isEmpty()) {
            return fallback;
        }
        List<Candidate> batch = candidates.subList(0, Math.min(AGENT_BATCH, candidates.size()));
        Map<Long, String> written = ask(batch, context);

        List<WrittenReason> result = new ArrayList<>(fallback.size());
        for (WrittenReason backup : fallback) {
            String reason = written.get(backup.activityId());
            result.add(reason != null && ReasonValidator.isAcceptable(reason)
                    ? new WrittenReason(backup.activityId(), reason.strip(), ReasonSource.AGENT)
                    : backup);
        }
        return result;
    }

    /** Frases por id de actividad; vacio si el modelo falla. Ignora ids que no se le pidieron (W3). */
    private Map<Long, String> ask(List<Candidate> batch, CoupleContext context) {
        Set<Long> asked = batch.stream().map(c -> c.activity().getId()).collect(Collectors.toSet());
        long started = System.nanoTime();
        try {
            String json = ollama.generateJson(PromptBuilder.build(batch, context), properties.llmTimeout());
            Map<Long, String> reasons = new HashMap<>();
            for (JsonNode item : mapper.readTree(json).path("reasons")) {
                long id = item.path("id").asLong(-1);
                JsonNode reason = item.path("reason");
                if (asked.contains(id) && reason.isTextual()) {
                    reasons.put(id, reason.asText());
                }
            }
            log.info("[T-06] Mistral redacto {} de {} frases en {} ms",
                    reasons.size(), batch.size(), (System.nanoTime() - started) / 1_000_000);
            return reasons;
        } catch (Exception e) {
            log.warn("[T-06] Frases del catalogo tras {} ms: {}", (System.nanoTime() - started) / 1_000_000, e.getMessage());
            return Map.of();
        }
    }
}
