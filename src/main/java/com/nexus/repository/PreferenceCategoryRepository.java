package com.nexus.repository;

import com.nexus.entity.PreferenceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PreferenceCategoryRepository extends JpaRepository<PreferenceCategory, Long> {
    
    @Query("SELECT pc FROM PreferenceCategory pc WHERE pc.deletedAt IS NULL ORDER BY pc.id")
    List<PreferenceCategory> findAllActive();
    
    @Query("SELECT pc FROM PreferenceCategory pc WHERE pc.name = :name AND pc.deletedAt IS NULL")
    Optional<PreferenceCategory> findByName(String name);
}
