-- cleanup_places_approx.sql ([kb-lugares] U5) -- escrito a mano.
-- Borra los 301 lugares aproximados (source = 'approx-borough', coordenadas = centro de la
-- alcaldia) y todo lo de prueba que los usa, antes de cargar el catalogo real.
--
-- ANTES DE CORRERLO: respaldo (R3). En cmd, desde la carpeta de MySQL (no esta en el PATH):
--   mysqldump -u root -p nexus > nexus_antes_kb.sql
--
-- Orden (no hay llaves foraneas en la planeacion en pareja, se borra a mano):
--   1. solicitudes de recomendacion con alguna opcion en un lugar viejo
--   2. sesiones de planeacion de esas solicitudes, con sus mensajes, votos y avisos
--   3. las solicitudes (sus recomendaciones se van por ON DELETE CASCADE)
--   4. los lugares viejos (sus 315 enlaces de activity_places se van por ON DELETE CASCADE)
-- Los eventos ya creados no se tocan: guardan el lugar como texto.
-- Todo va en una transaccion. Idempotente: la segunda vez no borra nada.

SET NAMES utf8mb4;
START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS kb_old_requests;
DROP TEMPORARY TABLE IF EXISTS kb_old_sessions;
CREATE TEMPORARY TABLE kb_old_requests (id BIGINT UNSIGNED PRIMARY KEY)
  SELECT DISTINCT r.request_id AS id
  FROM `nexus`.`recommendations` r JOIN `nexus`.`places` p ON p.id = r.place_id
  WHERE p.source = 'approx-borough';
CREATE TEMPORARY TABLE kb_old_sessions (id BIGINT UNSIGNED PRIMARY KEY)
  SELECT s.id FROM `nexus`.`planning_sessions` s JOIN kb_old_requests o ON o.id = s.request_id;

-- Conteos ANTES (MySQL no deja usar una tabla temporal dos veces en la misma consulta).
SELECT COUNT(*) AS solicitudes_a_borrar FROM kb_old_requests;
SELECT COUNT(*) AS recomendaciones_a_borrar FROM `nexus`.`recommendations` r JOIN kb_old_requests o ON o.id = r.request_id;
SELECT COUNT(*) AS sesiones_a_borrar FROM kb_old_sessions;
SELECT COUNT(*) AS votos_a_borrar FROM `nexus`.`planning_votes` v JOIN kb_old_sessions o ON o.id = v.session_id;
SELECT COUNT(*) AS mensajes_a_borrar FROM `nexus`.`planning_messages` m JOIN kb_old_sessions o ON o.id = m.session_id;
SELECT COUNT(*) AS avisos_a_borrar FROM `nexus`.`app_notifications` n JOIN kb_old_sessions o ON o.id = n.session_id;
SELECT COUNT(*) AS lugares_a_borrar FROM `nexus`.`places` WHERE source = 'approx-borough';
SELECT COUNT(*) AS eventos_antes FROM `nexus`.`events`;

DELETE m FROM `nexus`.`planning_messages` m JOIN kb_old_sessions o ON o.id = m.session_id;
DELETE v FROM `nexus`.`planning_votes` v JOIN kb_old_sessions o ON o.id = v.session_id;
DELETE n FROM `nexus`.`app_notifications` n JOIN kb_old_sessions o ON o.id = n.session_id;
DELETE s FROM `nexus`.`planning_sessions` s JOIN kb_old_sessions o ON o.id = s.id;
DELETE rr FROM `nexus`.`recommendation_requests` rr JOIN kb_old_requests o ON o.id = rr.id;
DELETE FROM `nexus`.`places` WHERE source = 'approx-borough';

COMMIT;
DROP TEMPORARY TABLE kb_old_requests;
DROP TEMPORARY TABLE kb_old_sessions;

-- Comprobaciones DESPUES (esperado: 0, 0, 0, 0 y los mismos eventos que antes).
SELECT COUNT(*) AS lugares_viejos FROM `nexus`.`places` WHERE source = 'approx-borough';
SELECT COUNT(*) AS enlaces_sinteticos FROM `nexus`.`activity_places` WHERE source = 'synthetic';
SELECT COUNT(*) AS sesiones_sin_solicitud FROM `nexus`.`planning_sessions` s
  LEFT JOIN `nexus`.`recommendation_requests` rr ON rr.id = s.request_id
  WHERE s.request_id IS NOT NULL AND rr.id IS NULL;
SELECT COUNT(*) AS votos_sin_opcion FROM `nexus`.`planning_votes` v
  LEFT JOIN `nexus`.`recommendations` r ON r.id = v.recommendation_id
  WHERE r.id IS NULL;
SELECT COUNT(*) AS eventos_despues FROM `nexus`.`events`;
