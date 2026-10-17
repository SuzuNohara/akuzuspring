package com.nexus.service.recommendation;

import com.nexus.entity.Activity;
import com.nexus.model.IdeaCategory;
import java.util.List;

/**
 * [T-06] U8 - Prompt de Mistral para redactar la frase de cada recomendacion. Solo lleva campos
 * cerrados del catalogo, recortados y saneados (RN-32, R11), y el tono que conviene segun el nivel
 * combinado del animo de la pareja (RN-28): nunca datos de una persona.
 */
public final class PromptBuilder {

    private static final int MAX_TITLE = 80;

    private PromptBuilder() {}

    public static String build(List<Candidate> candidates, CoupleContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Eres el asistente de citas de Nexus, una app para parejas en la Ciudad de México.\n");
        prompt.append("Para cada actividad escribe una frase breve en español (máximo 160 caracteres), ")
                .append("hablándole a la pareja de ustedes, que explique por qué les puede gustar.\n");
        prompt.append("No menciones emociones ni estados de ánimo. No inventes lugares, precios ni horarios. ")
                .append("No incluyas enlaces.\n");
        prompt.append("Tono: ").append(tone(context)).append(".\n");
        prompt.append("Actividades:\n");
        for (Candidate c : candidates) {
            Activity a = c.activity();
            prompt.append("- id ").append(a.getId())
                    .append(" | ").append(clean(a.getTitle()))
                    .append(" | Bienestar ").append(IdeaCategory.ofActivityType(a.getActivityType()).label())
                    .append(" | ").append("HOME".equals(a.getLocationScope()) ? "en casa" : "en la ciudad")
                    .append(" | ").append(a.getDurationMin()).append(" min\n");
        }
        prompt.append("Responde solo con JSON de esta forma: ")
                .append("{\"reasons\":[{\"id\":<id>,\"reason\":\"<frase>\"}]}");
        return prompt.toString();
    }

    private static String tone(CoupleContext context) {
        if (context.coupleEnergy() == MoodLevel.BAJO || context.coupleValence() == MoodLevel.BAJO) {
            return "tranquilo y cálido";
        }
        if (context.coupleEnergy() == MoodLevel.ALTO) {
            return "entusiasta";
        }
        return "cercano y sencillo";
    }

    /** Texto del catalogo en una sola linea, sin llaves, comillas ni simbolos de control. */
    static String clean(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("[\\p{Cntrl}\\r\\n]+", " ")
                .replaceAll("[{}\\[\\]\"`<>|\\\\]", "")
                .replaceAll("\\s+", " ")
                .strip();
        return flat.length() <= MAX_TITLE ? flat : flat.substring(0, MAX_TITLE);
    }
}
