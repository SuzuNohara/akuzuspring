package com.nexus.repository;

import com.nexus.entity.MoodEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Entradas de ánimo por usuario y fecha (nexus-EMO-01). */
public interface MoodEntryRepository extends JpaRepository<MoodEntry, Long> {

    Optional<MoodEntry> findByUserIdAndEntryDate(Long userId, LocalDate entryDate);

    List<MoodEntry> findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
            Long userId, LocalDate from, LocalDate to);

    @Query(
            "SELECT m FROM MoodEntry m WHERE m.user.id = :userId AND m.entryDate < :before"
                    + " ORDER BY m.entryDate DESC")
    List<MoodEntry> findPageBefore(
            @Param("userId") Long userId, @Param("before") LocalDate before, Pageable page);
}
