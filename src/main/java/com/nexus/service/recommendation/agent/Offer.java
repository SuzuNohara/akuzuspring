package com.nexus.service.recommendation.agent;

import com.nexus.model.IdeaCategory;
import java.util.List;
import lombok.Builder;

/**
 * [t06b-agente-java] U1 - Una oferta del catalogo: una actividad en casa, o una actividad en uno de
 * sus lugares. Es lo que se indexa en Qdrant (un punto por oferta, D3).
 *
 * @param placeId lugar en la ciudad; {@code null} si es en casa
 * @param interests nombres de las preferencias de {@code activity_interests}, en orden
 * @param ambience ambiente de la actividad y de su lugar, sin repetir
 * @param lat latitud del lugar; {@code null} en casa
 * @param lon longitud del lugar; {@code null} en casa
 */
@Builder(toBuilder = true)
public record Offer(
        long activityId,
        Long placeId,
        String title,
        String description,
        String rationale,
        String activityType,
        IdeaCategory dimension,
        List<String> interests,
        List<String> ambience,
        List<String> dayparts,
        String locationScope,
        String placeName,
        String borough,
        Double lat,
        Double lon,
        String priceBand,
        int costMxnPp,
        boolean outdoor,
        List<String> weatherOk,
        int durationMin,
        int durationAvg,
        int durationMax) {

    /** {@code "12"} en casa o {@code "12@40"} en el lugar 40; sirve para reportar descartes. */
    public String key() {
        return placeId == null ? String.valueOf(activityId) : activityId + "@" + placeId;
    }
}
