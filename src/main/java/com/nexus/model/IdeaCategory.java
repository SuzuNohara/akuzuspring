package com.nexus.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * RF-34 - Categorias del banco de ideas: las 7 dimensiones del bienestar del cuestionario de
 * preferencias ({@code pref_categories}). Tambien es el "tipo de plan" de las recomendaciones.
 *
 * <p>El catalogo clasifica cada actividad con un {@code activity_type} tecnico; aqui cada tipo
 * pertenece a una sola dimension. {@code OTHER} y cualquier tipo que no este en ninguna (p. ej.
 * uno nuevo del catalogo real) caen en {@link #FALLBACK}, asi que ampliar el catalogo nunca rompe
 * el banco.
 */
public enum IdeaCategory {
    FISICO("Físico", Set.of("SPORT_SESSION", "SWIM", "CLIMB", "CYCLE")),
    EMOCIONAL("Emocional", Set.of("DINNER", "STARGAZING")),
    SOCIAL(
            "Social",
            Set.of(
                    "FILM", "CONCERT", "SHOW", "COFFEE", "TASTING", "SHOPPING", "BOARD_GAME",
                    "VIDEOGAME", "OTHER")),
    INTELECTUAL(
            "Intelectual",
            Set.of(
                    "READING", "COURSE", "EXHIBITION_VISIT", "GUIDED_TOUR", "COOKING", "PUZZLE",
                    "PHOTO_WALK")),
    /** Planes de apoyo mutuo en metas y trabajo (dimension ocupacional, Swarbrick 2006). */
    PROFESIONAL("Profesional", Set.of("CAREER")),
    AMBIENTAL("Ambiental", Set.of("WALK", "PICNIC", "BOAT_RIDE", "CAMPING", "HIKE")),
    ESPIRITUAL("Espiritual", Set.of("MEDITATION", "VOLUNTEERING", "WORKSHOP"));

    /** Donde caen OTHER y los tipos desconocidos (decision de la usuaria, 2026-10-16). */
    public static final IdeaCategory FALLBACK = SOCIAL;

    private final String label;
    private final Set<String> activityTypes;

    IdeaCategory(String label, Set<String> activityTypes) {
        this.label = label;
        this.activityTypes = activityTypes;
    }

    /** Nombre que ve la persona, igual al de {@code pref_categories} sin "Bienestar". */
    public String label() {
        return label;
    }

    /** Valores de {@code activity_type} que agrupa esta dimension. */
    public Set<String> activityTypes() {
        return activityTypes;
    }

    /** Dimension de un {@code activity_type}; {@link #FALLBACK} si es desconocido o nulo. */
    public static IdeaCategory ofActivityType(String activityType) {
        if (activityType == null) {
            return FALLBACK; // Set.of(...).contains(null) lanza NPE
        }
        return Arrays.stream(values())
                .filter(category -> category.activityTypes.contains(activityType))
                .findFirst()
                .orElse(FALLBACK);
    }

    /** Interpreta el codigo recibido por la API (sin distinguir mayusculas). */
    public static Optional<IdeaCategory> parse(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(c -> c.name().equals(normalized)).findFirst();
    }
}
