-- add_export_events.sql ([sync-calendario] S-01) -- escrito a mano.
-- calendars.export_events: el calendario del telefono al que se copian los eventos de Nexus
-- confirmados (RF-22). Solo uno por persona; lo cuida el backend.
-- calendar_events.export_signature: firma (SHA-256) de lo que Nexus escribio la ultima vez en la
-- copia, para saber si hay que actualizarla.
-- MySQL no tiene ADD COLUMN IF NOT EXISTS: se consulta information_schema y se arma el ALTER.
-- Idempotente: se puede correr varias veces. Requiere add_app_event_id.sql.

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'nexus' AND TABLE_NAME = 'calendars' AND COLUMN_NAME = 'export_events');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `nexus`.`calendars` ADD COLUMN `export_events` BOOLEAN NOT NULL DEFAULT FALSE AFTER `sync_enabled`',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'nexus' AND TABLE_NAME = 'calendar_events' AND COLUMN_NAME = 'export_signature');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `nexus`.`calendar_events` ADD COLUMN `export_signature` VARCHAR(64) NULL AFTER `conflict_hash`',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Comprobacion: 2 columnas.
SELECT COUNT(*) AS columnas_nuevas FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'nexus'
  AND ((TABLE_NAME = 'calendars' AND COLUMN_NAME = 'export_events')
    OR (TABLE_NAME = 'calendar_events' AND COLUMN_NAME = 'export_signature'));
