package com.nexus.repository;

import com.nexus.entity.ExternalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalEventRepository extends JpaRepository<ExternalEvent, Long> {
    
    @Query("SELECT e FROM ExternalEvent e WHERE e.externalCalendarId = :calendarId AND e.deletedAt IS NULL")
    List<ExternalEvent> findByExternalCalendarId(Long calendarId);
    
    @Query("SELECT e FROM ExternalEvent e WHERE e.externalCalendarId IN :calendarIds AND e.deletedAt IS NULL")
    List<ExternalEvent> findByExternalCalendarIdIn(List<Long> calendarIds);
    
    @Query("SELECT e FROM ExternalEvent e WHERE e.externalCalendarId = :calendarId AND e.deviceEventId = :deviceEventId AND e.deletedAt IS NULL")
    Optional<ExternalEvent> findByExternalCalendarIdAndDeviceEventId(
        Long calendarId, 
        String deviceEventId
    );
    
    @Query("SELECT e FROM ExternalEvent e WHERE e.externalCalendarId IN :calendarIds " +
           "AND (" +
           "  (e.startDatetime <= :endDate AND e.endDatetime >= :startDate) " + // Eventos normales en el rango
           "  OR (" + // O eventos recurrentes que podrían tener ocurrencias en el rango
           "    e.recurrenceRule IS NOT NULL " +
           "    AND (" +
           "      e.rruleUntilUtc IS NULL " + // Recurrencia sin límite
           "      OR e.rruleUntilUtc >= :startDate" + // O recurrencia que termina después del inicio del rango
           "    )" +
           "    AND e.rruleDtstartUtc <= :endDate" + // Y que empezó antes del final del rango
           "  )" +
           ") " +
           "AND e.deletedAt IS NULL AND e.status != 'CANCELLED' " +
           "ORDER BY e.startDatetime ASC")
    List<ExternalEvent> findEventsByCalendarsAndDateRange(
        @Param("calendarIds") List<Long> calendarIds,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );
    
    @Modifying
    @Query("UPDATE ExternalEvent e SET e.deletedAt = CURRENT_TIMESTAMP WHERE e.externalCalendarId = :calendarId")
    void softDeleteByExternalCalendarId(Long calendarId);
    
    @Modifying
    @Query("UPDATE ExternalEvent e SET e.deletedAt = CURRENT_TIMESTAMP WHERE e.externalCalendarId = :calendarId AND e.deviceEventId = :deviceEventId")
    void softDeleteByExternalCalendarIdAndDeviceEventId(Long calendarId, String deviceEventId);
}
