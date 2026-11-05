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

    /** [primeros-pasos] R-06: si hay algun calendario activo, con los mismos filtros que la lista de arriba. */
    @Query("SELECT COUNT(c) > 0 FROM ExternalCalendar c WHERE c.userId = :userId AND c.isActive = true AND c.deletedAt IS NULL")
    boolean existsActiveByUserId(Long userId);

    @Query("SELECT c FROM ExternalCalendar c WHERE c.userId = :userId AND c.syncEnabled = true AND c.deletedAt IS NULL")
    List<ExternalCalendar> findByUserIdAndSyncEnabledTrue(Long userId);
    
    @Query("SELECT c FROM ExternalCalendar c WHERE c.userId = :userId AND c.deviceCalendarId = :deviceCalendarId AND c.deletedAt IS NULL")
    Optional<ExternalCalendar> findByUserIdAndDeviceCalendarId(Long userId, String deviceCalendarId);
    
    /** [sync-calendario] RF-22: el calendario al que se copian los eventos de Nexus. */
    @Query("SELECT c FROM ExternalCalendar c WHERE c.userId = :userId AND c.isActive = true AND c.exportEvents = true AND c.deletedAt IS NULL")
    Optional<ExternalCalendar> findExportTarget(Long userId);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM ExternalCalendar c WHERE c.userId = :userId AND c.deviceCalendarId = :deviceCalendarId AND c.deletedAt IS NULL")
    boolean existsByUserIdAndDeviceCalendarId(Long userId, String deviceCalendarId);
}
