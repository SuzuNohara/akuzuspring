package com.nexus.controller;

import com.nexus.dto.CreateEventRequest;
import com.nexus.dto.CreateEventResponse;
import com.nexus.dto.EventResponse;
import com.nexus.dto.UpdateEventRequest;
import com.nexus.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class EventController {
    
    private final EventService eventService;
    
    /**
     * CU14 - Crear evento
     * Permite que un usuario vinculado cree un nuevo evento
     */
    @PostMapping("/create/{userId}")
    public ResponseEntity<CreateEventResponse> createEvent(
            @PathVariable Long userId,
            @Valid @RequestBody CreateEventRequest request) {
        
        log.info("Solicitud de creación de evento para usuario: {} - Evento: {}", userId, request.getTitle());
        
        CreateEventResponse response = eventService.createEvent(userId, request);
        
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }
    
    /**
     * Obtener todos los eventos de un usuario
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<EventResponse>> getUserEvents(@PathVariable Long userId) {
        log.info("Obteniendo eventos para usuario: {}", userId);
        
        List<EventResponse> events = eventService.getUserEvents(userId);
        
        return ResponseEntity.ok(events);
    }
    
    /**
     * Obtener eventos pendientes de aprobación para un usuario
     */
    @GetMapping("/user/{userId}/pending-approval")
    public ResponseEntity<List<EventResponse>> getPendingApprovalEvents(@PathVariable Long userId) {
        log.info("Obteniendo eventos pendientes de aprobación para usuario: {}", userId);
        
        List<EventResponse> pendingEvents = eventService.getPendingApprovalEvents(userId);
        
        return ResponseEntity.ok(pendingEvents);
    }
    
    /**
     * Contar eventos pendientes de aprobación
     */
    @GetMapping("/user/{userId}/pending-count")
    public ResponseEntity<Long> countPendingApprovals(@PathVariable Long userId) {
        long count = eventService.countPendingApprovals(userId);
        return ResponseEntity.ok(count);
    }
    
    /**
     * Aprobar un evento
     */
    @PostMapping("/{eventId}/approve/{userId}")
    public ResponseEntity<EventResponse> approveEvent(
            @PathVariable Long eventId,
            @PathVariable Long userId) {
        
        log.info("Usuario {} aprobando evento {}", userId, eventId);
        
        EventResponse approvedEvent = eventService.approveEvent(userId, eventId);
        
        return ResponseEntity.ok(approvedEvent);
    }
    
    /**
     * CU17 - Editar evento
     * Permite actualizar un evento existente
     */
    @PutMapping("/{eventId}/user/{userId}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable Long eventId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateEventRequest request) {
        
        log.info("Solicitud de actualización de evento {} por usuario: {}", eventId, userId);
        
        EventResponse updatedEvent = eventService.updateEvent(eventId, userId, request);
        
        return ResponseEntity.ok(updatedEvent);
    }
    
    /**
     * Rechazar un evento
     */
    @PostMapping("/{eventId}/reject/{userId}")
    public ResponseEntity<EventResponse> rejectEvent(
            @PathVariable Long eventId,
            @PathVariable Long userId) {
        
        log.info("Usuario {} rechazando evento {}", userId, eventId);
        
        EventResponse rejectedEvent = eventService.rejectEvent(userId, eventId);
        
        return ResponseEntity.ok(rejectedEvent);
    }
    
    /**
     * CU19 - Eliminar evento
     */
    @DeleteMapping("/{eventId}/user/{userId}")
    public ResponseEntity<Void> deleteEvent(
            @PathVariable Long eventId,
            @PathVariable Long userId) {
        
        log.info("Usuario {} eliminando evento {}", userId, eventId);
        
        eventService.deleteEvent(eventId, userId);
        
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Agregar excepción a evento recurrente (eliminar instancia específica)
     */
    @PostMapping("/{eventId}/exceptions")
    public ResponseEntity<Void> addEventException(
            @PathVariable Long eventId,
            @RequestParam String exceptionDate,
            @RequestParam Long userId) {
        
        log.info("Usuario {} agregando excepción al evento {} para la fecha {}", userId, eventId, exceptionDate);
        
        eventService.addEventException(eventId, exceptionDate, userId);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Health check para eventos
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Nexus Events API is running");
    }
}