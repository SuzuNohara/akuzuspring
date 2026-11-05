package com.nexus.service;

import com.nexus.dto.OnboardingProgressResponse;
import com.nexus.dto.OnboardingProgressResponse.Couple;
import com.nexus.dto.OnboardingProgressResponse.Mine;
import com.nexus.dto.OnboardingProgressResponse.Partner;
import com.nexus.entity.Link;
import com.nexus.entity.User;
import com.nexus.repository.DateRatingRepository;
import com.nexus.repository.ExternalCalendarRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.repository.RecommendationRequestRepository;
import com.nexus.repository.UserPreferenceRepository;
import com.nexus.repository.UserRepository;
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
    private final RecommendationRequestRepository requests;
    private final DateRatingRepository ratings;
    private final UserRepository users;

    /** Hitos de {@code userId}, los de su pareja (si hay vinculo activo) y los de los dos. */
    @Transactional(readOnly = true)
    public OnboardingProgressResponse progressFor(Long userId) {
        Optional<Link> link = links.findActiveLinkByUserId(userId);
        if (link.isEmpty()) {
            // R-05: sin vinculo es el unico caso en que hay que cargar a la persona
            boolean schedule = users.findById(userId).map(OnboardingService::schedule).orElse(false);
            Mine me = new Mine(questionnaire(userId), false, calendar(userId), schedule);
            return new OnboardingProgressResponse(me, null, NO_COUPLE);
        }
        Link active = link.get();
        // R-05: findActiveLinkByUserId ya trae a los dos con JOIN FETCH
        boolean initiated = active.getInitiatorUser().getId().equals(userId);
        User self = initiated ? active.getInitiatorUser() : active.getPartnerUser();
        User partner = initiated ? active.getPartnerUser() : active.getInitiatorUser();
        Long partnerId = partner.getId();
        Mine me = new Mine(questionnaire(userId), true, calendar(userId), schedule(self));
        Partner theirs = new Partner(partner.getDisplayName(),
                questionnaire(partnerId), calendar(partnerId), schedule(partner));
        Couple couple = new Couple(
                requests.existsByLink_Id(active.getId()),
                ratings.existsForLink(active.getId()));
        return new OnboardingProgressResponse(me, theirs, couple);
    }

    private boolean questionnaire(Long userId) {
        return preferences.existsByUserId(userId);
    }

    private boolean calendar(Long userId) {
        return calendars.existsActiveByUserId(userId);
    }

    /**
     * R-03: cuenta si la persona guardo sus horarios alguna vez ({@code users.schedule_customized_at},
     * lo pone {@code AvailabilityService.saveUserSchedule}). Los de fabrica del trigger no lo ponen.
     */
    private static boolean schedule(User user) {
        return user.getScheduleCustomizedAt() != null;
    }
}
