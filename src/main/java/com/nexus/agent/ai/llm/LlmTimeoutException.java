package com.nexus.agent.ai.llm;

/** El LLM no respondió dentro del presupuesto (nexus-AGENT-00). El compositor cae a reglas (D-08). */
public final class LlmTimeoutException extends LlmException {

    public LlmTimeoutException(String message) {
        super(message);
    }

    public LlmTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
