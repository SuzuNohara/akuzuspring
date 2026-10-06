package com.nexus.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * RF-34 - Categorias del banco de ideas que ve la persona.
 *
 * <p>El catalogo clasifica cada actividad con un {@code activity_type} tecnico (30 valores, ver
 * kb/schema.md); demasiados para filtrar en una pantalla. Aqui se agrupan en categorias legibles.
 * Un tipo que no este en ninguna (p. ej. uno nuevo del catalogo real) cae en {@link #OTRAS}, asi
 * que ampliar el catalogo nunca rompe el banco.
 */
public enum IdeaCategory {
    COMER_Y_BEBER("Comer y beber", Set.of("DINNER", "TASTING", "COFFEE", "COOKING")),
    CULTURA(
            "Cultura y espectáculos",
            Set.of("FILM", "CONCERT", "SHOW", "EXHIBITION_VISIT", "GUIDED_TOUR")),
    AIRE_LIBRE(
            "Aire libre",
            Set.of("WALK", "PICNIC", "STARGAZING", "BOAT_RIDE", "CAMPING", "PHOTO_WALK", "HIKE")),
    DEPORTE("Deporte y movimiento", Set.of("SPORT_SESSION", "SWIM", "CLIMB", "CYCLE")),
    APRENDER("Aprender juntos", Set.of("WORKSHOP", "COURSE", "READING", "VOLUNTEERING")),
    JUEGOS_Y_RELAX("Juegos y relax", Set.of("BOARD_GAME", "PUZZLE", "VIDEOGAME", "MEDITATION")),
    OTRAS("Otras ideas", Set.of("SHOPPING", "OTHER"));

    private final String label;
    private final Set<String> activityTypes;

    IdeaCategory(String label, Set<String> activityTypes) {
        this.label = label;
        this.activityTypes = activityTypes;
    }

    /** Nombre que ve la persona. */
    public String label() {
        return label;
    }

    /** Valores de {@code activity_type} que agrupa esta categoria. */
    public Set<String> activityTypes() {
        return activityTypes;
    }

    /** Categoria de un {@code activity_type}; {@link #OTRAS} si es desconocido o nulo. */
    public static IdeaCategory ofActivityType(String activityType) {
        if (activityType == null) {
            return OTRAS; // Set.of(...).contains(null) lanza NPE
        }
        return Arrays.stream(values())
                .filter(category -> category.activityTypes.contains(activityType))
                .findFirst()
                .orElse(OTRAS);
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
