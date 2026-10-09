package com.nexus.controller;

import com.nexus.dto.MoodEntryResponse;
import com.nexus.dto.MoodHistoryResponse;
import com.nexus.dto.MoodOptionResponse;
import com.nexus.dto.RecordMoodRequest;
import com.nexus.dto.RecordMoodResponse;
import com.nexus.service.MoodService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Frontera HTTP del registro e historial de ánimo (nexus-EMO-01, CU31/CU32).
 *
 * <p>Ningún endpoint acepta el id de un tercero ni expone el promedio de CU40 (RN-28): la identidad
 * siempre es el actor. El punto único de identidad es {@link #actor(Long)}; hoy devuelve el id de la
 * ruta y, cuando aterrice nexus-AUTH-03, lo validará contra el principal (403 si difieren).
 */
@RestController
@RequestMapping("/mood")
@RequiredArgsConstructor
public class MoodController {

    private final MoodService moodService;

    @GetMapping("/options")
    public ResponseEntity<List<MoodOptionResponse>> options() {
        return ResponseEntity.ok(moodService.listOptions());
    }

    @GetMapping("/today/{userId}")
    public ResponseEntity<MoodEntryResponse> today(@PathVariable Long userId) {
        return moodService
                .findToday(actor(userId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/today/{userId}")
    public ResponseEntity<RecordMoodResponse> record(
            @PathVariable Long userId, @Valid @RequestBody RecordMoodRequest body) {
        return ResponseEntity.ok(moodService.recordToday(actor(userId), body.code()));
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<MoodHistoryResponse> history(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate before,
            @RequestParam(defaultValue = "60") int limit) {
        return ResponseEntity.ok(
                moodService.history(actor(userId), Optional.ofNullable(before), limit));
    }

    /**
     * Punto ÚNICO de identidad. Hoy devuelve {@code pathUserId}; nexus-AUTH-03 lo validará contra el
     * principal y lanzará 403 si difieren (regla B2).
     */
    private Long actor(Long pathUserId) {
        return pathUserId;
    }
}
