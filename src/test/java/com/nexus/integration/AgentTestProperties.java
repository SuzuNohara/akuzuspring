package com.nexus.integration;

import com.nexus.config.AgentProperties;
import com.nexus.config.AgentProperties.TimePolicy;
import java.net.URI;
import java.time.Duration;

/** Propiedades del agente para pruebas: servicios locales y un tiempo limite corto. */
public final class AgentTestProperties {

    private AgentTestProperties() {}

    public static AgentProperties defaults() {
        return with(true, true);
    }

    /** Los valores de prueba con el agente y el indexado al arrancar como se pidan. */
    public static AgentProperties with(boolean enabled, boolean indexOnStartup) {
        return of(enabled, indexOnStartup, Duration.ofMillis(300), TimePolicy.HARD);
    }

    /** Politica SOFT con el tope por llamada dado (tope por llamada HARD de 7 s). */
    public static AgentProperties soft(Duration softCap) {
        AgentProperties p = of(true, true, Duration.ofSeconds(7), TimePolicy.SOFT);
        return new AgentProperties(
                p.ollamaUrl(), p.chatModel(), p.embedModel(), p.qdrantUrl(), p.llmTimeout(), p.indexOnStartup(),
                p.enabled(), p.alias(), p.textVersion(), p.budget(), p.minLlm(), p.timePolicy(), p.topK(),
                p.llmPicks(), p.numCtx(), p.numPredict(), p.temperature(), p.maxAttempts(), softCap,
                p.placesPerBorough());
    }

    /** Los valores de prueba con el tope por llamada y la politica de tiempo dados. */
    public static AgentProperties of(boolean enabled, boolean indexOnStartup, Duration llmTimeout, TimePolicy policy) {
        return new AgentProperties(
                URI.create("http://ollama.test"),
                "mistral:7b",
                "nomic-embed-text",
                URI.create("http://qdrant.test"),
                llmTimeout,
                indexOnStartup,
                enabled,
                "nexus_catalog_active",
                "t2",
                Duration.ofSeconds(9),
                Duration.ofMillis(2500),
                policy,
                30,
                5,
                8192,
                600,
                0.2,
                2,
                Duration.ofSeconds(60),
                10);
    }
}
