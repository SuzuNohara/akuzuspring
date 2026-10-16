package com.nexus.util;

/**
 * RN-31 - Distancia entre dos puntos WGS84 con la formula de Haversine (seccion 2.4.7).
 *
 * <p>Usa el radio medio de la Tierra de la IUGG (6 371.0088 km). Para las distancias de la CDMX
 * (menos de 60 km) el error frente a la distancia geodesica real es de unos metros, muy por debajo
 * del radio de 15 km de RN-31.
 */
public final class GeoDistance {

    /** Radio medio de la Tierra en kilometros (IUGG). */
    public static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoDistance() {}

    /**
     * @param lat1 latitud del primer punto, en grados
     * @param lon1 longitud del primer punto, en grados
     * @param lat2 latitud del segundo punto, en grados
     * @param lon2 longitud del segundo punto, en grados
     * @return distancia en kilometros
     */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double sinHalfDeltaPhi = Math.sin(Math.toRadians(lat2 - lat1) / 2);
        double sinHalfDeltaLambda = Math.sin(Math.toRadians(lon2 - lon1) / 2);
        double h =
                sinHalfDeltaPhi * sinHalfDeltaPhi
                        + Math.cos(phi1) * Math.cos(phi2) * sinHalfDeltaLambda * sinHalfDeltaLambda;
        // min(1, h): el redondeo puede dejar h apenas por encima de 1 en puntos casi antipodas.
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(Math.min(1.0, h)));
    }
}
