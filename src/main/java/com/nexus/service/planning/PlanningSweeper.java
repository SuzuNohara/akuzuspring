package com.nexus.service.planning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RN-40 - Cada minuto vence invitaciones y sesiones y manda los recordatorios que tocan. */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class PlanningSweeper {

    private final PlanningService planning;

    @Scheduled(fixedDelayString = "${nexus.planning.sweep-ms:60000}", initialDelay = 30_000)
    public void sweep() {
        try {
            planning.sweep();
        } catch (RuntimeException e) {
            // Una tabla faltante o un conflicto no debe tumbar el planificador: se reintenta en un minuto.
            log.warn("No se pudo revisar las planeaciones: {}", e.getMessage());
        }
    }
}
