package com.nexus.repository;

import com.nexus.entity.AvailabilitySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface AvailabilityScheduleRepository extends JpaRepository<AvailabilitySchedule, Long> {

    /**
     * Obtener todos los horarios permitidos de un usuario
     */
    List<AvailabilitySchedule> findByUserId(Long userId);

    /**
     * Obtener horarios habilitados de un usuario
     */
    @Query("SELECT a FROM AvailabilitySchedule a WHERE a.user.id = :userId AND a.isEnabled = true ORDER BY a.dayOfWeek")
    List<AvailabilitySchedule> findEnabledByUserId(@Param("userId") Long userId);

    /**
     * Obtener configuración específica para un día y usuario
     */
    Optional<AvailabilitySchedule> findByUserIdAndDayOfWeek(Long userId, AvailabilitySchedule.DayOfWeek dayOfWeek);

    /**
     * Eliminar todos los horarios de un usuario
     */
    @Modifying
    @Transactional
    void deleteByUserId(Long userId);
}
