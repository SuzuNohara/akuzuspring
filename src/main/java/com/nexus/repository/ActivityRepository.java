package com.nexus.repository;

import com.nexus.entity.Activity;
import com.nexus.entity.BankCategory;
import com.nexus.entity.LocationScope;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Acceso de solo lectura a las actividades del banco de ideas (nexus-KB-02). */
@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    Optional<Activity> findBySlug(String slug);

    List<Activity> findAllByOrderByTitleAsc();

    List<Activity> findByBankCategoryOrderByTitleAsc(BankCategory category);

    List<Activity> findByLocationScope(LocationScope scope);
}
