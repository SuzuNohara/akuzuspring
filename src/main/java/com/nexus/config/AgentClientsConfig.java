package com.nexus.config;

import com.nexus.integration.ollama.OllamaClient;
import com.nexus.integration.qdrant.QdrantClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * [T-06] Clientes HTTP del agente con tiempos de conexion y lectura acotados, para que un servicio
 * caido nunca deje colgada una solicitud.
 */
@Configuration
public class AgentClientsConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /** Qdrant responde en milisegundos; si tarda mas de esto, algo anda mal. */
    private static final Duration QDRANT_READ_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Tope de lectura de Ollama. La redaccion tiene su propio limite ({@code llm-timeout}); este
     * tope solo evita hilos colgados, y deja margen para cargar el modelo en frio (R1).
     */
    private static final Duration OLLAMA_READ_TIMEOUT = Duration.ofMinutes(5);

    /** Margen sobre {@code soft-cap} para que el corte lo haga el compositor y no el socket. */
    private static final Duration SOFT_CAP_MARGIN = Duration.ofSeconds(30);

    @Bean
    public OllamaClient ollamaClient(AgentProperties properties) {
        return new OllamaClient(builder(ollamaReadTimeout(properties)), properties);
    }

    /** [t06b-agente-java] 5 minutos, o mas si el tope SOFT es mayor. */
    static Duration ollamaReadTimeout(AgentProperties properties) {
        Duration withMargin = properties.softCap().plus(SOFT_CAP_MARGIN);
        return withMargin.compareTo(OLLAMA_READ_TIMEOUT) > 0 ? withMargin : OLLAMA_READ_TIMEOUT;
    }

    @Bean
    public QdrantClient qdrantClient(AgentProperties properties) {
        return new QdrantClient(builder(QDRANT_READ_TIMEOUT), properties);
    }

    private static RestClient.Builder builder(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        return RestClient.builder().requestFactory(factory);
    }
}
