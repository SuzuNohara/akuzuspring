package com.nexus.controller;

import com.nexus.dto.IdeaCategoryResponse;
import com.nexus.dto.IdeaResponse;
import com.nexus.exception.BadRequestException;
import com.nexus.model.IdeaCategory;
import com.nexus.security.AuthenticatedUser;
import com.nexus.security.OwnershipGuard;
import com.nexus.service.IdeaService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * RF-34 - Banco de ideas de citas.
 *
 * <p>El catalogo es el mismo para todas las parejas, pero la ruta lleva {@code {userId}} para
 * aplicar RN-29 (vinculo activo) sobre el usuario autenticado -- de ahi {@link
 * OwnershipGuard#requireSelf}.
 */
@RestController
@RequestMapping("/ideas")
@RequiredArgsConstructor
@Slf4j
public class IdeaController {

    private final IdeaService ideaService;

    /**
     * GET /api/ideas/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<List<IdeaCategoryResponse>> getCategories() {
        return ResponseEntity.ok(ideaService.listCategories());
    }

    /**
     * GET /api/ideas/{userId}?category=CULTURA
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<IdeaResponse>> getIdeas(
            @PathVariable Long userId,
            @RequestParam(required = false) String category,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        OwnershipGuard.requireSelf(currentUser, userId);

        Optional<IdeaCategory> parsed = Optional.empty();
        if (category != null && !category.isBlank()) {
            parsed =
                    Optional.of(
                            IdeaCategory.parse(category)
                                    .orElseThrow(
                                            () -> new BadRequestException("Categoría desconocida: " + category)));
        }

        log.info("Consultando banco de ideas para usuario {} (categoria {})", userId, parsed);
        return ResponseEntity.ok(ideaService.listIdeas(userId, parsed));
    }
}
