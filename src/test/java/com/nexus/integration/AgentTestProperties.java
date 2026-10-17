package com.nexus.integration;

import com.nexus.config.AgentProperties;
import java.net.URI;
import java.time.Duration;

/** Propiedades del agente para pruebas: servicios locales y un tiempo limite corto. */
public final class AgentTestProperties {

    private AgentTestProperties() {}

    public static AgentProperties defaults() {
        return new AgentProperties(
                URI.create("http://ollama.test"),
                "mistral:7b",
                "nomic-embed-text",
                URI.create("http://qdrant.test"),
                "activities",
                Duration.ofMillis(300),
                40,
                true,
                true);
    }
}
