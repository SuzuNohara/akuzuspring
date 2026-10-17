package com.nexus.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
 * @param collection coleccion de Qdrant con el catalogo de actividades
 * @param llmTimeout tiempo maximo de la redaccion; si se excede se usa el texto del catalogo
 *     (CU40 FA02) para no pasar de los 10 s de RNF-12
 * @param candidatePool cuantas candidatas se piden a Qdrant antes de filtrar por proximidad
 * @param indexOnStartup si al arrancar se reindexa el catalogo cuando no coincide con Qdrant
 * @param enabled si las recomendaciones usan el agente (busqueda en Qdrant y frases de Mistral);
 *     apagado, todo sale del catalogo de MySQL
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
        boolean enabled) {}
