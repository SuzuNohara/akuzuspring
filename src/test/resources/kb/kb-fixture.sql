-- kb-fixture.sql (nexus-KB-02): dato mínimo para los IT del paquete KB.
-- 3 lugares, 4 actividades (una HOME sin lugar), 3 vínculos, 2 centroides, 3 celdas de traslado.

DELETE FROM activity_places;
DELETE FROM transit_times;
DELETE FROM borough_centroids;
DELETE FROM activities;
DELETE FROM places;

INSERT INTO places
  (id, slug, name, family, place_type, lat, lon, neighborhood, borough, opening_hours,
   is_outdoor, weather_ok, ambience, description, source, license, needs_review, price_band)
VALUES
  (1, 'parque-coyoacan', 'Parque de Coyoacán', 'park', 'park', 19.350000, -99.160000,
   'Del Carmen', 'Coyoacán', NULL, 1, 'SUNNY|CLOUDY', 'NATURE|QUIET', 'Parque arbolado',
   'osm', 'odbl', 0, 'FREE'),
  (2, 'museo-centro', 'Museo del Centro', 'museum', 'museum', 19.432600, -99.133200,
   'Centro', 'Cuauhtémoc', 'Tu-Su 10:00-18:00', 0, 'ANY', 'CULTURAL', 'Museo bajo techo',
   'osm', 'odbl', 0, 'LOW'),
  (3, 'cafe-roma', 'Café Roma', 'cafe', 'cafe', 19.410000, -99.160000,
   'Roma Norte', 'Cuauhtémoc', 'Mo-Su 08:00-22:00', 0, 'ANY', 'SOCIAL', 'Café de barrio',
   'osm', 'odbl', 0, NULL);

INSERT INTO activities
  (id, slug, title, activity_type, location_scope, dayparts, duration_min, duration_avg, duration_max,
   price_band, bank_category, wellness_dimension, cost_min_mxn, cost_max_mxn, is_outdoor,
   ambience, weather_ok, description, needs_review)
VALUES
  (1, 'opera-bellas-artes', 'Ópera en Bellas Artes', 'show', 'CITY', 'EVENING', 90, 120, 180,
   'HIGH', 'CULTURA_Y_ESPECTACULOS', 'INTELECTUAL', 300, 800, 0, 'CULTURAL', 'ANY', 'Noche de ópera', 0),
  (2, 'armar-rompecabezas', 'armar rompecabezas', 'game', 'HOME', 'ANY', 30, 60, 120,
   'FREE', 'JUEGOS_Y_RELAX', 'INTELECTUAL', 0, 0, 0, 'HOME', 'ANY', 'Rompecabezas en casa', 0),
  (3, 'picnic-parque', 'Picnic en el parque', 'outdoor', 'CITY', 'MORNING', 60, 120, 240,
   'LOW', 'AIRE_LIBRE', 'AMBIENTAL', 50, 200, 1, 'NATURE', 'SUNNY|CLOUDY', 'Picnic al aire libre', 0),
  (4, 'cafe-especialidad', 'Café de especialidad', 'food', 'CITY', 'AFTERNOON', 45, 60, 90,
   'LOW', 'COMER_Y_BEBER', 'SOCIAL', 80, 200, 0, 'SOCIAL', 'ANY', 'Café de especialidad', 0);

INSERT INTO activity_places (activity_id, place_id, cost_override, notes, source) VALUES
  (3, 1, NULL, '', 'rule'),
  (1, 2, NULL, '', 'rule'),
  (4, 3, NULL, '', 'rule');

INSERT INTO borough_centroids (borough, lat, lon, source) VALUES
  ('Coyoacán', 19.350000, -99.160000, 'osm'),
  ('Cuauhtémoc', 19.432600, -99.133200, 'osm');

INSERT INTO transit_times (origin_borough, dest_borough, minutes_p50, minutes_p75, source, n_trips) VALUES
  ('Coyoacán', 'Cuauhtémoc', 55, 70, 'eod2017', 412),
  ('Cuauhtémoc', 'Coyoacán', 58, 74, 'eod2017', 398),
  ('Coyoacán', 'Coyoacán', 37, 48, 'formula:v1', 12);
