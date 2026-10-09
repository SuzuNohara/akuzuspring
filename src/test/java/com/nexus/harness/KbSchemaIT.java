package com.nexus.harness;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Contrato de esquema de {@code add_kb_tt_columns.sql} contra MySQL 8 real (nexus-KB-02): columnas
 * TT de {@code activities}, tablas {@code borough_centroids} y {@code transit_times}, y la vista
 * {@code kb_records}. Sin contexto Spring: solo el contenedor, como {@link MigrationIdempotenceIT}.
 */
class KbSchemaIT extends MySqlSchemaSupport {

    private static JdbcTemplate jdbc() {
        return new JdbcTemplate(
                new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }

    private static boolean columnExists(String table, String column) {
        Integer n =
                jdbc().queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns"
                                + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                        Integer.class,
                        DB,
                        table,
                        column);
        return n != null && n > 0;
    }

    @Test
    @DisplayName("should have the TT columns on activities and places when the migration is applied")
    void shouldHaveTtColumnsWhenMigrationApplied() {
        assertThat(columnExists("activities", "bank_category")).isTrue();
        assertThat(columnExists("activities", "wellness_dimension")).isTrue();
        assertThat(columnExists("activities", "cost_min_mxn")).isTrue();
        assertThat(columnExists("activities", "cost_max_mxn")).isTrue();
        assertThat(columnExists("activities", "canonical_text")).isTrue();
        assertThat(columnExists("places", "price_band")).isTrue();
        assertThat(columnExists("places", "canonical_hash")).isTrue();
    }

    @Test
    @DisplayName("should create the borough_centroids table")
    void shouldCreateBoroughCentroidsTable() {
        Integer n =
                jdbc().queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables"
                                + " WHERE table_schema = ? AND table_name = 'borough_centroids'",
                        Integer.class,
                        DB);
        assertThat(n).isEqualTo(1);
    }

    @Test
    @DisplayName("should expose the twelve TT attributes in the kb_records view")
    void shouldExposeTwelveTtAttributesInKbRecordsView() {
        List<String> columns =
                jdbc().queryForList(
                        "SELECT column_name FROM information_schema.columns"
                                + " WHERE table_schema = ? AND table_name = 'kb_records'",
                        String.class,
                        DB);
        assertThat(columns)
                .contains(
                        "bank_category",
                        "wellness_dimension",
                        "activity_type",
                        "neighborhood",
                        "borough",
                        "lat",
                        "lon",
                        "price_band",
                        "opening_hours",
                        "ambience",
                        "weather_ok",
                        "is_outdoor");
    }

    @Test
    @DisplayName("should return a HOME activity with a null place in the view")
    void shouldReturnHomeActivityWithNullPlaceInView() {
        JdbcTemplate db = jdbc();
        db.update(
                "INSERT INTO activities (slug, title, activity_type, location_scope, dayparts,"
                        + " duration_min, duration_avg, duration_max, price_band, bank_category,"
                        + " wellness_dimension) VALUES"
                        + " ('schema-home-probe', 'probe', 'game', 'HOME', 'ANY', 10, 20, 30,"
                        + " 'FREE', 'JUEGOS_Y_RELAX', 'INTELECTUAL')");
        try {
            String placeSlug =
                    db.queryForObject(
                            "SELECT place_slug FROM kb_records WHERE activity_slug = 'schema-home-probe'",
                            String.class);
            assertThat(placeSlug).isNull();
        } finally {
            db.update("DELETE FROM activities WHERE slug = 'schema-home-probe'");
        }
    }

    @Test
    @DisplayName("should create the transit_times table with a unique directed pair")
    void shouldCreateTransitTimesTableWithUniquePair() {
        Integer n =
                jdbc().queryForObject(
                        "SELECT COUNT(*) FROM information_schema.statistics"
                                + " WHERE table_schema = ? AND table_name = 'transit_times'"
                                + " AND index_name = 'uk_transit_times_pair' AND non_unique = 0",
                        Integer.class,
                        DB);
        assertThat(n).isEqualTo(2); // dos columnas en la clave única (origin, dest)
    }

    @Test
    @DisplayName("should declare wellness_dimension as NOT NULL")
    void shouldDeclareWellnessDimensionNotNull() {
        String nullable =
                jdbc().queryForObject(
                        "SELECT is_nullable FROM information_schema.columns"
                                + " WHERE table_schema = ? AND table_name = 'activities'"
                                + " AND column_name = 'wellness_dimension'",
                        String.class,
                        DB);
        assertThat(nullable).isEqualTo("NO");
    }
}
