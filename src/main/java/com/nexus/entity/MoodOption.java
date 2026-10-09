package com.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Una de las 8 opciones del catálogo de ánimo (CU31), con sus coordenadas del circumplejo de
 * Russell. Catálogo de solo lectura desde la app: la fuente de verdad es {@code add_mood_tables.sql}.
 */
@Entity
@Table(name = "mood_options")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoodOption {

    @Id private Integer id;

    @Column(name = "code")
    private String code;

    private String label;

    private String emoji;

    @Column(name = "valence")
    private BigDecimal valence;

    @Column(name = "arousal")
    private BigDecimal arousal;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
