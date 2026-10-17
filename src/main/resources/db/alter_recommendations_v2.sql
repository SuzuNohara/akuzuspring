-- T-06 ola 3' (2026-10-16) - Ajustes al diseno aprobado en Figma.
--   * Presupuesto en pesos por persona (0-15000) en vez de banda: recommendation_requests.max_cost_mxn.
--   * La columna budget (banda) deja de usarse: se permite NULL.
--   * Se guardan 15 opciones por solicitud y se muestran de 5 en 5: recommendations.shown.
-- Idempotente: se puede correr varias veces. Requiere add_recommendation_tables.sql.

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'nexus' AND TABLE_NAME = 'recommendation_requests' AND COLUMN_NAME = 'max_cost_mxn');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `nexus`.`recommendation_requests` ADD COLUMN `max_cost_mxn` INT UNSIGNED NULL AFTER `scope`',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `nexus`.`recommendation_requests` MODIFY COLUMN `budget` VARCHAR(4) NULL;

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'nexus' AND TABLE_NAME = 'recommendations' AND COLUMN_NAME = 'shown');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `nexus`.`recommendations` ADD COLUMN `shown` TINYINT(1) NOT NULL DEFAULT 0 AFTER `reason_source`',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
