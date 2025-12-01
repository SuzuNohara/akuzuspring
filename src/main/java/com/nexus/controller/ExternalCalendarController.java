package com.nexus.controller;

import com.nexus.dto.*;
import com.nexus.service.ExternalCalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/calendars/external")
@RequiredArgsConstructor
@Slf4j
public class ExternalCalendarController {

    private final ExternalCalendarService externalCalendarService;

    /**
     * RF-19: Vincular un calendario externo
     * POST /api/calendars/external/link/:userId
     */
    @PostMapping("/link/{userId}")
    public ResponseEntity<ExternalCalendarDTO> linkCalendar(
        @PathVariable Long userId,
        @Valid @RequestBody LinkCalendarRequest request
    ) {
        try {
            log.info("POST /calendars/external/link/{} - Vinculando calendario", userId);
            ExternalCalendarDTO result = externalCalendarService.linkCalendar(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            log.error("Error vinculando calendario: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * RF-26: Desvincular un calendario externo
     * DELETE /api/calendars/external/unlink/:userId/:deviceCalendarId
     */
    @DeleteMapping("/unlink/{userId}/{deviceCalendarId}")
    public ResponseEntity<Void> unlinkCalendar(
        @PathVariable Long userId,
        @PathVariable String deviceCalendarId
    ) {
        try {
            log.info("DELETE /calendars/external/unlink/{}/{} - Desvinculando calendario", 
                userId, deviceCalendarId);
            externalCalendarService.unlinkCalendar(userId, deviceCalendarId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error desvinculando calendario: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Obtener calendarios vinculados de un usuario
     * GET /api/calendars/external/:userId
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<ExternalCalendarDTO>> getUserCalendars(
        @PathVariable Long userId,
        @RequestParam(required = false, defaultValue = "true") Boolean activeOnly
    ) {
        try {
            log.info("GET /calendars/external/{} - Obteniendo calendarios", userId);
            List<ExternalCalendarDTO> calendars = externalCalendarService
                .getUserCalendars(userId, activeOnly);
            return ResponseEntity.ok(calendars);
        } catch (Exception e) {
            log.error("Error obteniendo calendarios: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Actualizar configuración de un calendario
     * PATCH /api/calendars/external/:userId/:deviceCalendarId
     */
    @PatchMapping("/{userId}/{deviceCalendarId}")
    public ResponseEntity<ExternalCalendarDTO> updateCalendarSettings(
        @PathVariable Long userId,
        @PathVariable String deviceCalendarId,
        @RequestParam(required = false) Boolean syncEnabled,
        @RequestParam(required = false) String privacyMode
    ) {
        try {
            log.info("PATCH /calendars/external/{}/{} - Actualizando configuración", 
                userId, deviceCalendarId);
            ExternalCalendarDTO result = externalCalendarService.updateCalendarSettings(
                userId, deviceCalendarId, syncEnabled, privacyMode
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error actualizando configuración: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * RF-20 & RF-21: Sincronizar eventos desde el dispositivo
     * POST /api/calendars/external/sync/:userId
     */
    @PostMapping("/sync/{userId}")
    public ResponseEntity<Map<String, Object>> syncEvents(
        @PathVariable Long userId,
        @Valid @RequestBody List<SyncEventRequest> events
    ) {
        try {
            log.info("POST /calendars/external/sync/{} - Sincronizando {} eventos", 
                userId, events.size());
            Map<String, Object> result = externalCalendarService.syncEvents(userId, events);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error sincronizando eventos: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * RF-23: Obtener eventos externos con privacidad aplicada
     * GET /api/calendars/external/events/:userId
     */
    @GetMapping("/events/{userId}")
    public ResponseEntity<List<ExternalEventDTO>> getEventsWithPrivacy(
        @PathVariable Long userId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
        @RequestParam(required = false) Long partnerId
    ) {
        try {
            log.info("GET /calendars/external/events/{} - Obteniendo eventos externos", userId);
            List<ExternalEventDTO> events = externalCalendarService
                .getEventsWithPrivacy(userId, startDate, endDate, partnerId);
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            log.error("Error obteniendo eventos: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * RF-27: Encontrar espacios libres individuales
     * GET /api/calendars/external/availability/:userId
     */
    @GetMapping("/availability/{userId}")
    public ResponseEntity<List<AvailabilitySlot>> findFreeSlots(
        @PathVariable Long userId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
        @RequestParam(defaultValue = "60") Integer minDurationMinutes
    ) {
        try {
            log.info("GET /calendars/external/availability/{} - Buscando espacios libres", userId);
            List<AvailabilitySlot> freeSlots = externalCalendarService
                .findFreeSlots(userId, startDate, endDate, minDurationMinutes);
            return ResponseEntity.ok(freeSlots);
        } catch (Exception e) {
            log.error("Error buscando espacios libres: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * RF-28: Cruce de disponibilidad entre dos usuarios
     * GET /api/calendars/external/mutual-availability
     */
    @GetMapping("/mutual-availability")
    public ResponseEntity<List<AvailabilitySlot>> findMutualAvailability(
        @RequestParam Long user1Id,
        @RequestParam Long user2Id,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
        @RequestParam(defaultValue = "60") Integer minDurationMinutes
    ) {
        try {
            log.info("GET /calendars/external/mutual-availability - Usuarios {} y {}", 
                user1Id, user2Id);
            List<AvailabilitySlot> mutualSlots = externalCalendarService
                .findMutualAvailability(user1Id, user2Id, startDate, endDate, minDurationMinutes);
            return ResponseEntity.ok(mutualSlots);
        } catch (Exception e) {
            log.error("Error buscando disponibilidad mutua: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("External Calendar API is running");
    }
}
