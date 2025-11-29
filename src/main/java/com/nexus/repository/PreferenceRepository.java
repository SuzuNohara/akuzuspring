package com.nexus.repository;

import com.nexus.entity.Preference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PreferenceRepository extends JpaRepository<Preference, Long> {
    
    @Query("SELECT p FROM Preference p " +
           "LEFT JOIN FETCH p.category " +
           "WHERE p.category.id = :categoryId AND p.deletedAt IS NULL ORDER BY p.id")
    List<Preference> findByCategoryId(@Param("categoryId") Long categoryId);
    
    @Query("SELECT p FROM Preference p " +
           "LEFT JOIN FETCH p.category " +
           "WHERE p.deletedAt IS NULL ORDER BY p.category.id, p.id")
    List<Preference> findAllActive();
}
