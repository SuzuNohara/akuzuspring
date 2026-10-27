-- seed_activities_kb_nuevas.sql ([kb-lugares] U3, D5) -- escrito a mano, BORRADOR para revision.
-- 9 actividades de ciudad para los lugares de kb-final que no tenian actividad (D3): centros
-- comerciales, zoologicos, estadios y arenas, pistas de hielo, heladerias, boliches, lugares para
-- bailar y karts. Costos y duraciones aproximados (needs_review = 1) hasta revisarlos.
-- Dimension por activity_type (IdeaCategory): SHOPPING, SHOW, COFFEE y OTHER -> Social;
-- WALK -> Ambiental; SPORT_SESSION -> Fisico.
-- place_types usa los tipos de kb-final y los nuevos de scripts/kb_corrections.csv (MALL, ICE_RINK,
-- KARTING). noche-de-lucha-libre no tiene tipo: sus arenas se enlazan a mano (LINK).
-- Requiere la tabla activities (add_kb_tables.sql). Idempotente: re-ejecutarlo actualiza.
-- SET NAMES: desde cmd el cliente de mysql leeria el archivo en la pagina de codigos de Windows.

SET NAMES utf8mb4;

INSERT INTO `nexus`.`activities` (slug, title, activity_type, location_scope, place_types, dayparts, seasons, duration_min, duration_avg, duration_max, difficulty_physical, difficulty_mental, collaboration, preparation, requires_booking, equipment, cost_mxn_pp, price_band, is_outdoor, weather_ok, ambience, description, rationale, enriched_by, confidence, reviewed_by, needs_review) VALUES
  ('paseo-por-un-centro-comercial', 'Paseo por un centro comercial', 'SHOPPING', 'CITY', 'MALL', 'AFTERNOON|NIGHT', 'ANY', 90, 150, 240, 1, 0, 5, 0, 0, '', 200, 'LOW', 0, 'ANY', 'SOCIAL', 'Recorrer juntos un centro comercial: ver tiendas, comer algo y, si se antoja, entrar al cine.', 'Un plan sin prisa y bajo techo les deja platicar mientras descubren cosas que les gustan a los dos.', 'manual', 1.0, NULL, 1),
  ('visitar-el-zoologico', 'Visitar el zoológico', 'WALK', 'CITY', 'ZOO', 'AFTERNOON|MORNING', 'ANY', 120, 180, 240, 2, 0, 5, 1, 0, '', 50, 'LOW', 1, 'CLOUDY|SUNNY', 'NATURE|SOCIAL', 'Recorrer el zoológico viendo a los animales y escoger juntos su favorito.', 'Caminar al aire libre y sorprenderse con algo nuevo los relaja y les da de qué platicar.', 'manual', 1.0, NULL, 1),
  ('ir-a-un-partido', 'Ir a un partido', 'SHOW', 'CITY', 'STADIUM', 'AFTERNOON|NIGHT', 'ANY', 120, 180, 240, 1, 0, 6, 2, 1, '', 500, 'MID', 1, 'ANY', 'ACTIVE|SOCIAL', 'Ir al estadio a ver un partido de futbol, beisbol u otro deporte y echarle porras al mismo equipo.', 'Emocionarse por lo mismo al mismo tiempo los une y les deja recuerdos compartidos.', 'manual', 1.0, NULL, 1),
  ('noche-de-lucha-libre', 'Noche de lucha libre', 'SHOW', 'CITY', '', 'NIGHT', 'ANY', 120, 150, 180, 0, 0, 5, 1, 1, '', 350, 'MID', 0, 'ANY', 'CULTURAL|SOCIAL', 'Ver una función de lucha libre en la arena: máscaras, gritos y botanas.', 'Un clásico de la ciudad que se disfruta más acompañado y entre risas.', 'manual', 1.0, NULL, 1),
  ('patinaje-sobre-hielo', 'Patinaje sobre hielo', 'SPORT_SESSION', 'CITY', 'ICE_RINK', 'AFTERNOON|NIGHT', 'ANY', 60, 90, 120, 6, 1, 7, 0, 0, '', 180, 'LOW', 0, 'ANY', 'ACTIVE|ROMANTIC', 'Rentar patines y dar vueltas en la pista, tomados de la mano para no caerse.', 'Aprender juntos algo en lo que los dos son torpes da risa y confianza.', 'manual', 1.0, NULL, 1),
  ('por-un-helado', 'Ir por un helado', 'COFFEE', 'CITY', 'ICE_CREAM', 'AFTERNOON|NIGHT', 'ANY', 30, 45, 60, 1, 0, 4, 0, 0, '', 80, 'LOW', 0, 'ANY', 'ROMANTIC|SOCIAL', 'Escoger sabores nuevos en una heladería y probar el del otro.', 'Un antojo corto cabe en cualquier día y es un buen pretexto para verse.', 'manual', 1.0, NULL, 1),
  ('noche-de-boliche', 'Noche de boliche', 'OTHER', 'CITY', 'BOWLING', 'AFTERNOON|NIGHT', 'ANY', 60, 120, 180, 3, 1, 6, 0, 0, '', 200, 'LOW', 0, 'ANY', 'ACTIVE|SOCIAL', 'Jugar unas líneas de boliche y llevar el marcador de quién gana.', 'Competir en broma los hace reír y les quita el estrés de la semana.', 'manual', 1.0, NULL, 1),
  ('salir-a-bailar', 'Salir a bailar', 'OTHER', 'CITY', 'NIGHTCLUB', 'NIGHT', 'ANY', 120, 180, 300, 5, 0, 8, 1, 0, '', 400, 'MID', 0, 'ANY', 'ACTIVE|ROMANTIC|SOCIAL', 'Ir a un lugar con pista y bailar salsa, cumbia o lo que toque esa noche.', 'Bailar juntos los acerca y deja salir la energía acumulada.', 'manual', 1.0, NULL, 1),
  ('carreras-de-karts', 'Carreras de karts', 'SPORT_SESSION', 'CITY', 'KARTING', 'AFTERNOON|MORNING', 'ANY', 45, 60, 90, 4, 1, 4, 0, 0, '', 350, 'MID', 1, 'CLOUDY|SUNNY', 'ACTIVE|SOCIAL', 'Correr unas vueltas en karts y ver quién hace el mejor tiempo.', 'La adrenalina compartida y el pique sano los hace sentir más cerca.', 'manual', 1.0, NULL, 1)
ON DUPLICATE KEY UPDATE title = VALUES(title), activity_type = VALUES(activity_type), location_scope = VALUES(location_scope), place_types = VALUES(place_types), dayparts = VALUES(dayparts), seasons = VALUES(seasons), duration_min = VALUES(duration_min), duration_avg = VALUES(duration_avg), duration_max = VALUES(duration_max), difficulty_physical = VALUES(difficulty_physical), difficulty_mental = VALUES(difficulty_mental), collaboration = VALUES(collaboration), preparation = VALUES(preparation), requires_booking = VALUES(requires_booking), equipment = VALUES(equipment), cost_mxn_pp = VALUES(cost_mxn_pp), price_band = VALUES(price_band), is_outdoor = VALUES(is_outdoor), weather_ok = VALUES(weather_ok), ambience = VALUES(ambience), description = VALUES(description), rationale = VALUES(rationale), enriched_by = VALUES(enriched_by), needs_review = VALUES(needs_review);

-- Comprobacion (P3.1, P3.4): las 9 con su tipo y el titulo con acentos.
SELECT slug, activity_type, title FROM `nexus`.`activities`
WHERE slug IN ('paseo-por-un-centro-comercial', 'visitar-el-zoologico', 'ir-a-un-partido', 'noche-de-lucha-libre',
               'patinaje-sobre-hielo', 'por-un-helado', 'noche-de-boliche', 'salir-a-bailar', 'carreras-de-karts')
ORDER BY slug;
