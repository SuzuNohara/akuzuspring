package com.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Registro de ánimo de un usuario para un día natural (RF-31, RN-38). El código viaja cifrado en
 * {@code mood_cipher} vía {@link MoodCodeConverter}; en el dominio es un {@code String}.
 *
 * <p>Sin {@code @Data}/{@code @ToString} a propósito: un {@code toString} con el código sería una
 * fuga (RN-28/RNF-11). El valor que Hibernate compara para el dirty checking es el {@code String}
 * descifrado, no el blob, así que un flush sin cambios no re-cifra ni toca {@code updated_at}.
 */
@Entity
@Table(name = "mood_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoodEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Convert(converter = MoodCodeConverter.class)
    @Column(name = "mood_cipher", nullable = false)
    private String moodCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
