package com.nexus.service.recommendation.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * [t06b-agente-java] U9 - Las tres plantillas de una version del prompt
 * ({@code resources/prompts/compose-<version>.*.txt}): el {@code system} (nivel {@code full} del lab
 * adaptado a "tú / tu pareja" y al animo combinado), el {@code user} y el {@code retry}.
 */
public record PromptTemplate(String system, String user, String retry, String version) {

    private static final String FOLDER = "prompts/";

    /**
     * @throws TemplateException si falta alguno de los tres archivos
     */
    public static PromptTemplate load(String version) {
        String base = FOLDER + "compose-" + version + ".";
        return new PromptTemplate(read(base + "system.txt"), read(base + "user.txt"), read(base + "retry.txt"), version);
    }

    private static String read(String path) {
        try (InputStream in = PromptTemplate.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new TemplateException("No existe la plantilla " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
        } catch (IOException e) {
            throw new TemplateException("No se pudo leer la plantilla " + path, e);
        }
    }
}
