package com.nexus.integration.ollama;

/** [t06b-agente-java] U10 - Un mensaje del chat de Ollama ({@code system}, {@code user} o {@code assistant}). */
public record ChatMessage(String role, String content) {}
