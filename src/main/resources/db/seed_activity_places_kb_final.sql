-- seed_activity_places_kb_final.sql ([kb-lugares] U4) -- GENERADO con scripts/gen_seed_kb_places.py.
-- Enlaces actividad x lugar: por tipo (place_types de la actividad x place_type del lugar) y
-- manuales (LINK de scripts/kb_corrections.csv). Correr despues de seed_places_kb_final.sql y
-- seed_activities_kb_nuevas.sql. Idempotente.

SET NAMES utf8mb4;

INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source)
SELECT a.id, p.id, 'kb-type' FROM `nexus`.`activities` a
JOIN `nexus`.`places` p ON FIND_IN_SET(p.place_type, REPLACE(REPLACE(a.place_types, ' ', ''), '|', ',')) > 0
WHERE a.location_scope = 'CITY' AND p.source IN ('osm', 'inegi-denue')
ON DUPLICATE KEY UPDATE source = VALUES(source);

-- Enlaces manuales (LINK).
INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source) SELECT a.id, p.id, 'manual' FROM `nexus`.`activities` a JOIN `nexus`.`places` p ON p.slug = 'cuauhtemoc-arena-mexico' WHERE a.slug = 'noche-de-lucha-libre' ON DUPLICATE KEY UPDATE source = VALUES(source);
INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source) SELECT a.id, p.id, 'manual' FROM `nexus`.`activities` a JOIN `nexus`.`places` p ON p.slug = 'cuauhtemoc-arena-coliseo' WHERE a.slug = 'noche-de-lucha-libre' ON DUPLICATE KEY UPDATE source = VALUES(source);
INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source) SELECT a.id, p.id, 'manual' FROM `nexus`.`activities` a JOIN `nexus`.`places` p ON p.slug = 'xochimilco-embarcadero-nuevo-nativitas' WHERE a.slug = 'paseo-en-trajinera' ON DUPLICATE KEY UPDATE source = VALUES(source);
INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source) SELECT a.id, p.id, 'manual' FROM `nexus`.`activities` a JOIN `nexus`.`places` p ON p.slug = 'xochimilco-embarcadero-las-flores' WHERE a.slug = 'paseo-en-trajinera' ON DUPLICATE KEY UPDATE source = VALUES(source);
INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source) SELECT a.id, p.id, 'manual' FROM `nexus`.`activities` a JOIN `nexus`.`places` p ON p.slug = 'xochimilco-embarcadero-cuemanco' WHERE a.slug = 'paseo-en-trajinera' ON DUPLICATE KEY UPDATE source = VALUES(source);
INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source) SELECT a.id, p.id, 'manual' FROM `nexus`.`activities` a JOIN `nexus`.`places` p ON p.slug = 'xochimilco-embarcadero-fernando-celada' WHERE a.slug = 'paseo-en-trajinera' ON DUPLICATE KEY UPDATE source = VALUES(source);
INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source) SELECT a.id, p.id, 'manual' FROM `nexus`.`activities` a JOIN `nexus`.`places` p ON p.slug = 'xochimilco-embarcadero-zacapa' WHERE a.slug = 'paseo-en-trajinera' ON DUPLICATE KEY UPDATE source = VALUES(source);
INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source) SELECT a.id, p.id, 'manual' FROM `nexus`.`activities` a JOIN `nexus`.`places` p ON p.slug = 'xochimilco-embarcadero-salitre' WHERE a.slug = 'paseo-en-trajinera' ON DUPLICATE KEY UPDATE source = VALUES(source);
INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source) SELECT a.id, p.id, 'manual' FROM `nexus`.`activities` a JOIN `nexus`.`places` p ON p.slug = 'xochimilco-embarcadero-puente-de-urrutia' WHERE a.slug = 'paseo-en-trajinera' ON DUPLICATE KEY UPDATE source = VALUES(source);
INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source) SELECT a.id, p.id, 'manual' FROM `nexus`.`activities` a JOIN `nexus`.`places` p ON p.slug = 'xochimilco-pista-olimpica-de-remo-y-canotaje-virgilio-uribe' WHERE a.slug = 'remo-en-el-lago' ON DUPLICATE KEY UPDATE source = VALUES(source);
INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source) SELECT a.id, p.id, 'manual' FROM `nexus`.`activities` a JOIN `nexus`.`places` p ON p.slug = 'tlahuac-parque-lago-de-los-reyes' WHERE a.slug = 'remo-en-el-lago' ON DUPLICATE KEY UPDATE source = VALUES(source);
INSERT INTO `nexus`.`activity_places` (activity_id, place_id, source) SELECT a.id, p.id, 'manual' FROM `nexus`.`activities` a JOIN `nexus`.`places` p ON p.slug = 'xochimilco-recorridos-en-kayak-xochimilco' WHERE a.slug = 'remo-en-el-lago' ON DUPLICATE KEY UPDATE source = VALUES(source);

-- Comprobacion 1: actividades de ciudad sin ningun lugar (esperado: ninguna).
SELECT a.slug FROM `nexus`.`activities` a
LEFT JOIN `nexus`.`activity_places` ap ON ap.activity_id = a.id
WHERE a.location_scope = 'CITY' AND ap.place_id IS NULL;

-- Comprobacion 2: lugares sin ninguna actividad, por tipo.
SELECT p.place_type, COUNT(*) AS lugares FROM `nexus`.`places` p
LEFT JOIN `nexus`.`activity_places` ap ON ap.place_id = p.id
WHERE ap.place_id IS NULL GROUP BY p.place_type ORDER BY lugares DESC;

-- Comprobacion 3: enlaces por actividad (sesgo de restaurantes, R de U4).
SELECT a.slug, COUNT(*) AS lugares FROM `nexus`.`activity_places` ap
JOIN `nexus`.`activities` a ON a.id = ap.activity_id GROUP BY a.slug ORDER BY lugares DESC;
