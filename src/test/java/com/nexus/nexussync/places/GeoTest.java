package com.nexus.nexussync.places;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.nexussync.context.Location;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class GeoTest {

  private static final double KM_TOLERANCE = 0.001;
  private static final double ONE_METRE_KM = 0.001;

  private static JsonNode reference() throws IOException {
    try (InputStream in =
        GeoTest.class.getResourceAsStream("/nexussync/places/geo-reference.json")) {
      assertThat(in).isNotNull();
      return new ObjectMapper().readTree(in);
    }
  }

  // U9-08
  @Test
  void givenPythonReferencePairs_whenHaversineKm_thenMatchesWithinOneMetre() throws IOException {
    for (JsonNode pair : reference().get("pairs")) {
      double km =
          Geo.haversineKm(
              pair.get("lat1").asDouble(),
              pair.get("lon1").asDouble(),
              pair.get("lat2").asDouble(),
              pair.get("lon2").asDouble());
      assertThat(km)
          .as(pair.get("from").asText())
          .isCloseTo(pair.get("km").asDouble(), within(KM_TOLERANCE));
    }
  }

  // U9-08
  @Test
  void givenPythonReferenceMidpoint_whenMidpoint_thenWithinOneMetre() throws IOException {
    JsonNode m = reference().get("midpoint");
    Location a = new Location(m.get("lat1").asDouble(), m.get("lon1").asDouble());
    Location b = new Location(m.get("lat2").asDouble(), m.get("lon2").asDouble());
    Location mid = Geo.midpoint(a, b);
    double error =
        Geo.haversineKm(mid.lat(), mid.lon(), m.get("lat").asDouble(), m.get("lon").asDouble());
    assertThat(error).isLessThan(ONE_METRE_KM);
  }

  @Test
  void givenTwoLocations_whenDistanceKm_thenEqualsHaversineAndIsSymmetric() {
    Location a = new Location(19.432608, -99.133209);
    Location b = new Location(19.349914, -99.16217);
    assertThat(Geo.distanceKm(a, b))
        .isEqualTo(Geo.haversineKm(a.lat(), a.lon(), b.lat(), b.lon()))
        .isCloseTo(Geo.distanceKm(b, a), within(1e-12));
  }
}
