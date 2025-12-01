package com.nexus.repository;

import com.nexus.entity.ExternalCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalCalendarRepository extends JpaRepository<ExternalCalendar, Long> {
    
    List<ExternalCalendar> findByUserId(Long userId);
    
    @Query("SELECT c FROM ExternalCalendar c WHERE c.userId = :userId AND c.isActive = true AND c.deletedAt IS NULL")
    List<ExternalCalendar> findByUserIdAndIsActiveTrue(Long userId);
    
    @Query("SELECT c FROM ExternalCalendar c WHERE c.userId = :userId AND c.syncEnabled = true AND c.deletedAt IS NULL")
    List<ExternalCalendar> findByUserIdAndSyncEnabledTrue(Long userId);
    
    @Query("SELECT c FROM ExternalCalendar c WHERE c.userId = :userId AND c.deviceCalendarId = :deviceCalendarId AND c.deletedAt IS NULL")
    Optional<ExternalCalendar> findByUserIdAndDeviceCalendarId(Long userId, String deviceCalendarId);
    
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM ExternalCalendar c WHERE c.userId = :userId AND c.deviceCalendarId = :deviceCalendarId AND c.deletedAt IS NULL")
    boolean existsByUserIdAndDeviceCalendarId(Long userId, String deviceCalendarId);
}
