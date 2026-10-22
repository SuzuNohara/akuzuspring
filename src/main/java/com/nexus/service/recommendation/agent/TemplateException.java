package com.nexus.service.recommendation.agent;

/** [t06b-agente-java] U9 - Falta una plantilla, o al renderizarla falto o sobro un valor. */
public class TemplateException extends RuntimeException {

    public TemplateException(String message) {
        super(message);
    }

    public TemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
