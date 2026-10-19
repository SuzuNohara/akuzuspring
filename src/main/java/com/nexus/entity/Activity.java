package com.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * RF-34 - Actividad del catalogo (tabla {@code activities}, creada por add_kb_tables.sql,
 * nexus-IDEAS-01; atributos de la seccion 3.10.2 del documento tecnico).
 *
 * <p>Solo lectura desde la app: el catalogo se carga con scripts de datos, nunca desde la API.
 * Se mapean unicamente las columnas que usan el banco de ideas y las recomendaciones.
 */
@Entity
@Immutable
@Table(name = "activities")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Activity {

    @Id private Long id;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(name = "activity_type", nullable = false)
    private String activityType;

    /** {@code HOME} o {@code CITY}. */
    @Column(name = "location_scope", nullable = false)
    private String locationScope;

    /** Lo minimo que dura; decide si cabe en la franja libre de la pareja. */
    @Column(name = "duration_min", nullable = false)
    private Integer durationMin;

    @Column(name = "duration_avg", nullable = false)
    private Integer durationAvg;

    /** {@code MORNING}, {@code AFTERNOON}, {@code NIGHT} separados por {@code |}, o {@code ANY}. */
    @Column(nullable = false)
    private String dayparts;

    /** Por que le conviene a una pareja; frase de respaldo cuando no redacta el agente. */
    @Column(nullable = false)
    private String rationale;

    @Column(name = "cost_mxn_pp", nullable = false)
    private Integer costMxnPp;

    /** {@code FREE}, {@code LOW}, {@code MID}, {@code HIGH} o {@code PREMIUM}. */
    @Column(name = "price_band", nullable = false)
    private String priceBand;

    @Column(name = "is_outdoor", nullable = false)
    private Boolean outdoor;

    /**
     * Climas con los que se puede hacer al aire libre: {@code ANY} o {@code SUNNY} / {@code CLOUDY}
     * separados por {@code |} (CU40, ver {@link com.nexus.model.WeatherCondition#allows}).
     */
    @Column(name = "weather_ok", nullable = false)
    private String weatherOk;
}
