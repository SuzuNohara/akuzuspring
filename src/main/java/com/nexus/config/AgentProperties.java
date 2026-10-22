package com.nexus.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * [T-06] Configuracion del agente de recomendaciones, enlazada al prefijo {@code nexus.agent}.
 *
 * <p>Los valores locales estan en application.properties; cualquiera se puede cambiar por
 * variable de entorno (p. ej. {@code NEXUS_AGENT_OLLAMA_URL} para usar Ollama en otro equipo).
 *
 * @param ollamaUrl direccion de Ollama
 * @param chatModel modelo que redacta las frases (Mistral 7B, seccion 2.17)
 * @param embedModel modelo de embeddings
 * @param qdrantUrl direccion de la API REST de Qdrant
 * @param collection coleccion vieja de Qdrant ({@code ActivityIndexer}); se quita al borrarlo (T-37)
 * @param llmTimeout tope de cada llamada a Mistral; con politica HARD tambien lo acota lo que
 *     quede del presupuesto
 * @param candidatePool candidatas por busqueda de la busqueda vieja; se quita en la limpieza (T-37)
 * @param indexOnStartup si al arrancar se reindexa el catalogo cuando no coincide con Qdrant
 * @param enabled si las recomendaciones usan el agente (busqueda en Qdrant y frases de Mistral);
 *     apagado, todo sale del catalogo de MySQL
 * @param alias [t06b-agente-java] alias de Qdrant que apunta a la coleccion vigente
 * @param textVersion version del texto canonico de las ofertas
 * @param budget presupuesto de toda la solicitud, desde la busqueda hasta la composicion (RNF-12)
 * @param minLlm tiempo minimo restante para llamar a Mistral; con menos, la lista sale por reglas
 * @param timePolicy {@code HARD} respeta el presupuesto; {@code SOFT} deja terminar a Mistral (solo
 *     para pruebas locales)
 * @param topK candidatas que se recuperan de Qdrant (una por actividad)
 * @param llmPicks cuantas elige y justifica Mistral (k)
 * @param numCtx contexto de Mistral en tokens
 * @param numPredict tope de tokens de la respuesta
 * @param temperature temperatura de Mistral
 * @param maxAttempts intentos con Mistral (el segundo es el reintento con la respuesta invalida)
 */
@Validated
@ConfigurationProperties(prefix = "nexus.agent")
public record AgentProperties(
        @NotNull URI ollamaUrl,
        @NotBlank String chatModel,
        @NotBlank String embedModel,
        @NotNull URI qdrantUrl,
        @NotBlank String collection,
        @NotNull Duration llmTimeout,
        @Positive int candidatePool,
        boolean indexOnStartup,
        boolean enabled,
        @DefaultValue("nexus_catalog_active") @NotBlank String alias,
        @DefaultValue("t2") @NotBlank String textVersion,
        @DefaultValue("9s") @NotNull Duration budget,
        @DefaultValue("2500ms") @NotNull Duration minLlm,
        @DefaultValue("HARD") @NotNull TimePolicy timePolicy,
        @DefaultValue("30") @Positive int topK,
        @DefaultValue("5") @Positive int llmPicks,
        @DefaultValue("8192") @Positive int numCtx,
        @DefaultValue("600") @Positive int numPredict,
        @DefaultValue("0.2") double temperature,
        @DefaultValue("2") @Positive int maxAttempts) {

    /** Que pasa cuando Mistral no termina dentro del presupuesto. */
    public enum TimePolicy {
        /** El presupuesto manda: sin tiempo, la lista sale por reglas (RNF-12). */
        HARD,
        /** Mistral termina aunque se pase (tope de 60 s); solo para medir en local. */
        SOFT
    }
}
