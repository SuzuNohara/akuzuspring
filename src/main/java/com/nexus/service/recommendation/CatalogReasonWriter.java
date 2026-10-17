package com.nexus.service.recommendation;

import com.nexus.entity.Activity;
import com.nexus.model.ReasonSource;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * CU40 FA02 - Frase de cada recomendacion tomada del catalogo ({@code activities.rationale}). Es la
 * frase mientras no esta conectado el agente y su respaldo por elemento despues.
 */
@Service
public class CatalogReasonWriter implements ReasonWriter {

    /** Largo maximo que se muestra (el validador del agente usa el mismo). */
    static final int MAX_LENGTH = 200;

    private static final String ELLIPSIS = "…";

    /**
     * {@inheritDoc}
     *
     * @implNote O(n) con n candidatas.
     */
    @Override
    public List<WrittenReason> write(List<Candidate> candidates, CoupleContext context) {
        return candidates.stream()
                .map(Candidate::activity)
                .map(a -> new WrittenReason(a.getId(), reasonOf(a), ReasonSource.CATALOG))
                .toList();
    }

    private static String reasonOf(Activity a) {
        String text = a.getRationale() == null || a.getRationale().isBlank() ? a.getDescription() : a.getRationale();
        String trimmed = text == null ? "" : text.strip();
        return trimmed.length() <= MAX_LENGTH ? trimmed : trimmed.substring(0, MAX_LENGTH - 1) + ELLIPSIS;
    }
}
