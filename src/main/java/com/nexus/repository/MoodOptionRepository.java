package com.nexus.repository;

import com.nexus.entity.MoodOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso de solo lectura al catálogo de opciones de ánimo (nexus-EMO-01). */
public interface MoodOptionRepository extends JpaRepository<MoodOption, Integer> {

    List<MoodOption> findAllByOrderBySortOrderAsc();
}
