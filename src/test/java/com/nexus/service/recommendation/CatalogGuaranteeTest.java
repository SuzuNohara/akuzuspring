package com.nexus.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S12 / R16 - Con la franja minima (60 min) y todos los filtros ampliados, el catalogo sembrado debe
 * dar al menos las 15 opciones que se guardan por solicitud: actividades en casa que caben en una
 * hora (en casa no hay filtro de distancia).
 */
class CatalogGuaranteeTest {

    private static final Path SEEDS = Path.of("src/main/resources/db");
    private static final List<String> FILES = List.of("seed_activities_synthetic.sql", "seed_activities_profesional.sql");
    /** slug, title, activity_type, location_scope, place_types, dayparts, seasons, duration_min. */
    private static final Pattern ROW = Pattern.compile(
            "^\\s*\\('[^']*', '(?:[^']|'')*', '[A-Z_]+', '(HOME|CITY)', '[^']*', '[^']*', '[^']*', (\\d+),");

    @Test
    @DisplayName("hay al menos 15 actividades en casa con duracion minima de 60 min o menos")
    void enoughHomeActivitiesForMinimumSlot() throws IOException {
        long fitting = 0;
        for (String file : FILES) {
            for (String line : Files.readAllLines(SEEDS.resolve(file), StandardCharsets.UTF_8)) {
                Matcher m = ROW.matcher(line);
                if (m.find() && "HOME".equals(m.group(1)) && Integer.parseInt(m.group(2)) <= 60) {
                    fitting++;
                }
            }
        }
        assertThat(fitting).isGreaterThanOrEqualTo(15);
    }
}
