package com.nexus.controller;

import com.nexus.backend.service.AvailabilityService;
import com.nexus.dto.AvailabilityScheduleDTO;
import com.nexus.entity.AvailabilitySchedule;
import com.nexus.entity.User;
import com.nexus.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/availability")
public class AvailabilityController {

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private UserRepository userRepository;

    /**
     * Calcula los espacios disponibles de un usuario
     * GET /api/availability/calculate/:userId?days=7
     */
    @GetMapping("/calculate/{userId}")
    public ResponseEntity<Map<String, Object>> calculateAvailableSlots(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "7") int days) {
        
        try {
            Map<String, Object> result = availabilityService.calculateAvailableSlots(userId, days);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "error", "Error al calcular espacios disponibles",
                    "message", e.getMessage()
                ));
        }
    }

    /**
     * Encuentra disponibilidad mutua entre dos usuarios
     * GET /api/availability/mutual/:userId1/:userId2?days=7
     * CU29 - Comparar espacios de ambos usuarios
     */
    @GetMapping("/mutual/{userId1}/{userId2}")
    public ResponseEntity<Map<String, Object>> findMutualAvailability(
            @PathVariable Long userId1,
            @PathVariable Long userId2,
            @RequestParam(defaultValue = "7") int days) {
        
        try {
            Map<String, Object> result = availabilityService.findMutualAvailability(userId1, userId2, days);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "error", "Error al calcular disponibilidad mutua",
                    "message", e.getMessage()
                ));
        }
    }

    /**
     * Obtiene la configuración de horarios permitidos de un usuario
     * GET /api/availability/schedule/{userId}
     */
    @GetMapping("/schedule/{userId}")
    public ResponseEntity<?> getUserSchedule(@PathVariable Long userId) {
        try {
            var schedule = availabilityService.getUserSchedule(userId);
            return ResponseEntity.ok(schedule);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Error al obtener horarios",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Guarda la configuración de horarios permitidos de un usuario
     * PUT /api/availability/schedule/{userId}
     * Body: [ { day: 'MONDAY', enabled: true, startTime: '09:00', endTime: '22:00' }, ... ]
     */
    @PutMapping("/schedule/{userId}")
    public ResponseEntity<?> saveUserSchedule(
        @PathVariable Long userId,
        @RequestBody java.util.List<AvailabilityScheduleDTO> body
    ) {
        try {
            System.out.println("📥 Recibiendo request para guardar horarios de usuario: " + userId);
            System.out.println("📋 Cantidad de días recibidos: " + body.size());
            
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                System.out.println("❌ Usuario no encontrado: " + userId);
                return ResponseEntity.status(404).body(Map.of(
                    "error", "Usuario no encontrado"
                ));
            }

            System.out.println("✅ Usuario encontrado: " + user.getDisplayName());

            // Convertir DTOs a entidades
            java.util.List<AvailabilitySchedule> entities = new java.util.ArrayList<>();
            
            for (AvailabilityScheduleDTO dto : body) {
                try {
                    System.out.println("  📅 Procesando día: " + dto.getDay() + 
                        " enabled=" + dto.getEnabled() + 
                        " " + dto.getStartTime() + "-" + dto.getEndTime());
                    
                    AvailabilitySchedule.DayOfWeek dayEnum = AvailabilitySchedule.DayOfWeek.valueOf(dto.getDay());
                    
                    java.time.LocalTime startTime = java.time.LocalTime.parse(dto.getStartTime());
                    java.time.LocalTime endTime = java.time.LocalTime.parse(dto.getEndTime());
                    
                    System.out.println("    ⏰ Parsed times: start=" + startTime + " end=" + endTime);
                    
                    // Validar que end_time > start_time
                    if (!endTime.isAfter(startTime)) {
                        throw new IllegalArgumentException("End time must be after start time: " + 
                            dto.getStartTime() + " >= " + dto.getEndTime());
                    }
                    
                    AvailabilitySchedule entity = AvailabilitySchedule.builder()
                        .user(user)
                        .dayOfWeek(dayEnum)
                        .isEnabled(dto.getEnabled() != null ? dto.getEnabled() : false)
                        .startTime(startTime)
                        .endTime(endTime)
                        .build();
                    
                    entities.add(entity);
                } catch (Exception e) {
                    System.out.println("❌ Error procesando día " + dto.getDay() + ": " + e.getMessage());
                    e.printStackTrace();
                    throw e;
                }
            }

            System.out.println("💾 Guardando " + entities.size() + " horarios...");
            var saved = availabilityService.saveUserSchedule(userId, entities);
            System.out.println("✅ Horarios guardados exitosamente");
            
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            System.out.println("❌ Error en saveUserSchedule: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Error al guardar horarios",
                "message", e.getMessage(),
                "type", e.getClass().getSimpleName()
            ));
        }
    }
}
