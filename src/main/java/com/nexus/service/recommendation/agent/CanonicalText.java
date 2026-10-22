package com.nexus.service.recommendation.agent;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * [t06b-agente-java] U2 - Funciones puras que hacen determinista el indexado: el texto que se
 * vectoriza, su hash, el id del punto y el nombre de la coleccion (igual que {@code canonical.py}
 * del lab de Suzu).
 *
 * <p>El texto lleva solo semantica: coordenadas, aire libre y clima se quedan fuera porque son
 * filtros duros.
 */
public final class CanonicalText {

    private static final Map<String, String> PRICE_LABEL =
            Map.of("FREE", "gratis", "LOW", "bajo", "MID", "medio", "HIGH", "alto");

    private static final String NONE = "ninguno";
    private static final Pattern NOT_SLUG = Pattern.compile("[^a-z0-9-]");

    /** {@code NAMESPACE_URL} de RFC 4122, el mismo que usa {@code uuid.uuid5} en Python. */
    private static final UUID NAMESPACE_URL = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");

    private CanonicalText() {}

    /**
     * Las 5 lineas fijas: Actividad / Tipo y Bienestar / Intereses y Ambiente / Lugar / Precio y
     * Duracion.
     *
     * @implNote O(largo de los campos).
     */
    public static String of(Offer o) {
        String place = o.placeName() != null && o.borough() != null
                ? o.placeName() + ", " + o.borough()
                : o.placeName();
        String price = PRICE_LABEL.getOrDefault(o.priceBand(), o.priceBand().toLowerCase(Locale.ROOT));
        return String.join(
                "\n",
                "Actividad: " + o.title() + ". " + o.description(),
                "Tipo: " + words(o.activityType()) + ". Bienestar: Bienestar " + o.dimension().label() + ".",
                "Intereses: " + join(o.interests()) + ". Ambiente: "
                        + join(o.ambience().stream().map(a -> a.toLowerCase(Locale.ROOT)).toList()) + ".",
                "Lugar: " + (place == null ? "en casa" : place) + ".",
                "Precio: " + price + ", " + o.costMxnPp() + " MXN por persona. Duración: " + o.durationAvg()
                        + " min (" + o.durationMin() + " a " + o.durationMax() + ").");
    }

    /**
     * SHA-256 en hexadecimal de {@code modelTag|textVersion|text}: si cambia el modelo, la version del
     * texto o el texto, la oferta se vuelve a vectorizar.
     *
     * @implNote O(largo del texto).
     */
    public static String hash(String modelTag, String textVersion, String text) {
        byte[] digest = digest("SHA-256", (modelTag + "|" + textVersion + "|" + text).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    /**
     * Id estable del punto: UUID v5 de {@code act:<id>|place:<id|HOME>}.
     *
     * @implNote O(1).
     */
    public static String pointId(Offer o) {
        String name = "act:" + o.activityId() + "|place:" + (o.placeId() == null ? "HOME" : o.placeId());
        return uuid5(NAMESPACE_URL, name).toString();
    }

    /** {@code nexus_catalog__<modelo>__d<dimension>__<version>}; fuera de {@code [a-z0-9-]} se vuelve {@code -}. */
    public static String collectionName(String modelTag, int dimension, String textVersion) {
        String slug = NOT_SLUG.matcher(modelTag.toLowerCase(Locale.ROOT)).replaceAll("-");
        return "nexus_catalog__" + slug + "__d" + dimension + "__" + textVersion;
    }

    private static UUID uuid5(UUID namespace, String name) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocate(16 + nameBytes.length)
                .putLong(namespace.getMostSignificantBits())
                .putLong(namespace.getLeastSignificantBits())
                .put(nameBytes);
        byte[] hash = digest("SHA-1", input.array());
        hash[6] = (byte) ((hash[6] & 0x0f) | 0x50); // version 5
        hash[8] = (byte) ((hash[8] & 0x3f) | 0x80); // variant RFC 4122
        ByteBuffer bits = ByteBuffer.wrap(hash, 0, 16);
        return new UUID(bits.getLong(), bits.getLong());
    }

    private static byte[] digest(String algorithm, byte[] input) {
        try {
            return MessageDigest.getInstance(algorithm).digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " no esta disponible en la JVM", e);
        }
    }

    /** {@code SPORT_SESSION} → {@code sport session}. */
    private static String words(String enumValue) {
        return enumValue.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String join(List<String> items) {
        return items == null || items.isEmpty() ? NONE : items.stream().collect(Collectors.joining(", "));
    }
}
