package com.nexus.service;

import com.nexus.dto.IdeaCategoryResponse;
import com.nexus.dto.IdeaResponse;
import com.nexus.entity.Activity;
import com.nexus.exception.ForbiddenException;
import com.nexus.model.IdeaCategory;
import com.nexus.repository.ActivityRepository;
import com.nexus.repository.LinkRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * RF-34 - Banco de ideas: catalogo de actividades organizado por categoria.
 *
 * <p>RN-29: solo con vinculo activo. RF-35 (proponer una idea) no vive aqui: la app prellena el
 * flujo normal de creacion de eventos (RF-14) y la pareja lo aprueba con RF-30, sin un segundo
 * mecanismo de aprobacion.
 */
@Service
@RequiredArgsConstructor
public class IdeaService {

    private final ActivityRepository activityRepository;
    private final LinkRepository linkRepository;

    public List<IdeaResponse> listIdeas(Long userId, Optional<IdeaCategory> category) {
        if (!linkRepository.existsActiveLinkByUserId(userId)) {
            throw new ForbiddenException(
                    "Necesitas un vínculo activo con tu pareja para usar el banco de ideas.");
        }
        return findActivities(category).stream().map(IdeaService::toResponse).toList();
    }

    public List<IdeaCategoryResponse> listCategories() {
        return Arrays.stream(IdeaCategory.values())
                .map(c -> IdeaCategoryResponse.builder().code(c.name()).label(c.label()).build())
                .toList();
    }

    private List<Activity> findActivities(Optional<IdeaCategory> category) {
        if (category.isEmpty()) {
            return activityRepository.findAllByOrderByTitleAsc();
        }
        if (category.get() == IdeaCategory.OTRAS) {
            // OTRAS tambien recoge tipos que ninguna categoria conoce, asi que no basta un IN.
            return activityRepository.findAllByOrderByTitleAsc().stream()
                    .filter(a -> IdeaCategory.ofActivityType(a.getActivityType()) == IdeaCategory.OTRAS)
                    .toList();
        }
        return activityRepository.findByActivityTypeInOrderByTitleAsc(
                category.get().activityTypes());
    }

    private static IdeaResponse toResponse(Activity activity) {
        IdeaCategory category = IdeaCategory.ofActivityType(activity.getActivityType());
        return IdeaResponse.builder()
                .id(activity.getId())
                .title(activity.getTitle())
                .description(activity.getDescription())
                .category(category.name())
                .categoryLabel(category.label())
                .atHome("HOME".equals(activity.getLocationScope()))
                .outdoor(Boolean.TRUE.equals(activity.getOutdoor()))
                .durationMinutes(activity.getDurationAvg())
                .priceBand(activity.getPriceBand())
                .costPerPersonMxn(activity.getCostMxnPp())
                .build();
    }
}
