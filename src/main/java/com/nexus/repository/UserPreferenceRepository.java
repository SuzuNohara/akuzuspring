package com.nexus.repository;

import com.nexus.entity.UserPreference;
import com.nexus.entity.UserPreferenceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, UserPreferenceId> {
    
    @Query("SELECT up FROM UserPreference up " +
           "LEFT JOIN FETCH up.preference p " +
           "LEFT JOIN FETCH p.category " +
           "WHERE up.user.id = :userId ORDER BY p.category.id, p.id")
    List<UserPreference> findByUserId(@Param("userId") Long userId);
    
    @Query("SELECT COUNT(up) > 0 FROM UserPreference up WHERE up.user.id = :userId")
    boolean existsByUserId(@Param("userId") Long userId);
}
