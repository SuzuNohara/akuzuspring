-- add_app_event_id.sql ([avisos-calendario] U1) -- escrito a mano.
-- calendar_events.app_event_id: evento de Nexus del que salio la copia en el calendario del telefono
-- (al aceptar un evento y agregarlo al calendario externo). NULL = evento personal, que nunca genera
-- conflicto (RF-24).
-- calendar_events.conflict_hash: huella del ultimo cambio del que ya se aviso, para avisar una sola
-- vez por cambio (sync_hash ya se reescribe en cada sincronizacion).
-- MySQL no tiene ADD COLUMN IF NOT EXISTS: se consulta information_schema y se arma el ALTER.
-- Idempotente: se puede correr varias veces. Requiere add_calendar_events_external_columns.sql.

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'nexus' AND TABLE_NAME = 'calendar_events' AND COLUMN_NAME = 'app_event_id');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `nexus`.`calendar_events` ADD COLUMN `app_event_id` BIGINT NULL AFTER `device_event_id`, ADD KEY `idx_calendar_events_app_event` (`app_event_id`)',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'nexus' AND TABLE_NAME = 'calendar_events' AND COLUMN_NAME = 'conflict_hash');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `nexus`.`calendar_events` ADD COLUMN `conflict_hash` VARCHAR(64) NULL AFTER `sync_hash`',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Comprobacion: 2 columnas.
SELECT COUNT(*) AS columnas_nuevas FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'nexus' AND TABLE_NAME = 'calendar_events'
  AND COLUMN_NAME IN ('app_event_id', 'conflict_hash');
