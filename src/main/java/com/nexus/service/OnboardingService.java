package com.nexus.service;

import com.nexus.dto.OnboardingProgressResponse;
import com.nexus.dto.OnboardingProgressResponse.Couple;
import com.nexus.dto.OnboardingProgressResponse.Mine;
import com.nexus.dto.OnboardingProgressResponse.Partner;
import com.nexus.entity.Link;
import com.nexus.entity.User;
import com.nexus.repository.AvailabilityScheduleRepository;
import com.nexus.repository.DateRatingRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.repository.RecommendationRequestRepository;
import com.nexus.repository.UserPreferenceRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [primeros-pasos] Avance de los 6 hitos de la tarjeta "Primeros pasos", calculado con los datos
 * reales (no hay contador aparte): si alguien desconecta su calendario, el hito vuelve a pendiente.
 */
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final Couple NO_COUPLE = new Couple(false, false);

    private final UserPreferenceRepository preferences;
    private final LinkRepository links;
    private final ExternalCalendarRepository calendars;
    private final AvailabilityScheduleRepository schedules;
    private final RecommendationRequestRepository requests;
    private final DateRatingRepository ratings;

    /** Hitos de {@code userId}, los de su pareja (si hay vinculo activo) y los de los dos. */
    @Transactional(readOnly = true)
    public OnboardingProgressResponse progressFor(Long userId) {
        Optional<Link> link = links.findActiveLinkByUserId(userId);
        Mine me = new Mine(questionnaire(userId), link.isPresent(), calendar(userId), schedule(userId));
        if (link.isEmpty()) {
            return new OnboardingProgressResponse(me, null, NO_COUPLE);
        }
        Link active = link.get();
        User partner = partnerOf(active, userId);
        Long partnerId = partner.getId();
        Partner theirs = new Partner(partner.getDisplayName(),
                questionnaire(partnerId), calendar(partnerId), schedule(partnerId));
        // Solo cuenta lo de este vinculo: una calificacion de antes de vincularse no es de esta pareja.
        Couple couple = new Couple(
                requests.existsByLink_Id(active.getId()),
                ratings.existsByUserIdInAndCreatedAtGreaterThanEqual(
                        List.of(userId, partnerId), active.getStartedAt()));
        return new OnboardingProgressResponse(me, theirs, couple);
    }

    private static User partnerOf(Link link, Long userId) {
        User initiator = link.getInitiatorUser();
        return initiator.getId().equals(userId) ? link.getPartnerUser() : initiator;
    }

    private boolean questionnaire(Long userId) {
        return preferences.existsByUserId(userId);
    }

    private boolean calendar(Long userId) {
        return !calendars.findByUserIdAndIsActiveTrue(userId).isEmpty();
    }

    /** No se guardan horarios por defecto: hay filas solo si la persona ya los guardo. */
    private boolean schedule(Long userId) {
        return !schedules.findByUserId(userId).isEmpty();
    }
}
