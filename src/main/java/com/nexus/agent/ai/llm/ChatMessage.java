package com.nexus.agent.ai.llm;

/** Un turno de la conversación con el LLM (nexus-AGENT-00). */
public record ChatMessage(Role role, String content) {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }
}
