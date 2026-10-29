-- add_radius_km.sql ([rango-distancia] U5) -- escrito a mano.
-- radius_km: distancia maxima que eligio la pareja (1, 3, 5, 10 o 15 km). NULL = 15 km, el valor
-- de antes, asi que las sesiones y solicitudes viejas siguen igual.
-- MySQL no tiene ADD COLUMN IF NOT EXISTS: se consulta information_schema y se arma el ALTER.
-- Idempotente: se puede correr varias veces. Requiere add_planning_sessions.sql,
-- add_recommendation_tables.sql y alter_recommendations_v2.sql.

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'nexus' AND TABLE_NAME = 'planning_sessions' AND COLUMN_NAME = 'radius_km');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `nexus`.`planning_sessions` ADD COLUMN `radius_km` TINYINT NULL AFTER `max_cost_mxn`',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'nexus' AND TABLE_NAME = 'recommendation_requests' AND COLUMN_NAME = 'radius_km');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `nexus`.`recommendation_requests` ADD COLUMN `radius_km` TINYINT NULL AFTER `max_cost_mxn`',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Comprobacion: 2 columnas radius_km.
SELECT COUNT(*) AS radius_km FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'nexus' AND COLUMN_NAME = 'radius_km'
  AND TABLE_NAME IN ('planning_sessions', 'recommendation_requests');
