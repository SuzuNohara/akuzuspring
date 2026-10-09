package com.nexus.agent.ai.compose;

/**
 * Una recomendación compuesta (nexus-AGENT-00): la actividad y el lugar elegidos y el motivo que se
 * le muestra a la pareja. El título sale del catálogo, no del LLM (D-05).
 */
public record RecommendationDraft(String activityId, String placeId, String title, String reason) {}
