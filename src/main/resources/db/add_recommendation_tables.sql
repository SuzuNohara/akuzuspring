-- add_recommendation_tables.sql ([T-06] RF-33, RF-36, RF-37, RF-40, RN-31, RN-39)
--
-- Tablas del agente de recomendaciones en modalidad individual. La sesion conjunta
-- (recommendation_sessions, session_messages) queda para RF-38/RF-39 y no se crea aqui.
--
-- Requiere: Nexus.sql (users, user_links, events) y add_kb_tables.sql (activities, places).
-- Idempotente: se puede correr mas de una vez sin error ni cambios.

-- RN-31: centro de cada una de las 16 alcaldias (modo "alcaldia" del filtro de proximidad).
CREATE TABLE IF NOT EXISTS `nexus`.`boroughs` (
  `id` TINYINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(32) NOT NULL,
  `name` VARCHAR(60) NOT NULL,
  `lat` DECIMAL(9,6) NOT NULL,
  `lon` DECIMAL(9,6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_boroughs_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Parametros de cada solicitud (CU36/CU39). Solo valores cerrados (RN-32).
-- No guarda la ubicacion del usuario: "cerca de nosotros" se usa y se descarta (RN-30).
CREATE TABLE IF NOT EXISTS `nexus`.`recommendation_requests` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requester_user_id` INT UNSIGNED NOT NULL,
  `link_id` BIGINT UNSIGNED NOT NULL,
  `mode` VARCHAR(12) NOT NULL,           -- INDIVIDUAL (JOINT llega con RF-38)
  `zone_type` VARCHAR(10) NOT NULL,      -- NEAR_US | BOROUGH | NONE
  `borough_id` TINYINT UNSIGNED NULL,
  `scope` VARCHAR(4) NOT NULL,           -- HOME | CITY | ANY
  `budget` VARCHAR(4) NOT NULL,          -- FREE | LOW | MID | HIGH (tope)
  `category` VARCHAR(20) NULL,           -- IdeaCategory; NULL = cualquiera
  `slot_start` DATETIME(3) NOT NULL,
  `slot_end` DATETIME(3) NOT NULL,
  `rounds_used` TINYINT UNSIGNED NOT NULL DEFAULT 1,   -- RN-39: maximo 3
  `status` VARCHAR(8) NOT NULL DEFAULT 'OPEN',          -- OPEN | SENT
  `version` INT UNSIGNED NOT NULL DEFAULT 0,            -- bloqueo optimista (R15)
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_rr_requester` (`requester_user_id`, `created_at`),
  CONSTRAINT `fk_rr_user` FOREIGN KEY (`requester_user_id`) REFERENCES `nexus`.`users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rr_link` FOREIGN KEY (`link_id`) REFERENCES `nexus`.`user_links` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rr_borough` FOREIGN KEY (`borough_id`) REFERENCES `nexus`.`boroughs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Cada actividad mostrada. UNIQUE(request_id, activity_id): una actividad no se repite dentro
-- de la misma solicitud, ni siquiera despues de descartarla (RN-39).
CREATE TABLE IF NOT EXISTS `nexus`.`recommendations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `request_id` BIGINT UNSIGNED NOT NULL,
  `activity_id` BIGINT UNSIGNED NOT NULL,
  `place_id` BIGINT UNSIGNED NULL,
  `round` TINYINT UNSIGNED NOT NULL,
  `position` TINYINT UNSIGNED NOT NULL,
  `reason` VARCHAR(300) NOT NULL,
  `reason_source` VARCHAR(8) NOT NULL,   -- AGENT | CATALOG
  `discarded` TINYINT(1) NOT NULL DEFAULT 0,
  `chosen` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rec_request_activity` (`request_id`, `activity_id`),
  CONSTRAINT `fk_rec_request` FOREIGN KEY (`request_id`) REFERENCES `nexus`.`recommendation_requests` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rec_activity` FOREIGN KEY (`activity_id`) REFERENCES `nexus`.`activities` (`id`),
  CONSTRAINT `fk_rec_place` FOREIGN KEY (`place_id`) REFERENCES `nexus`.`places` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- events.activity_id: de que actividad salio el evento (recomendacion o banco de ideas).
-- RF-42 pondera por categoria y la necesita. NULL en todos los eventos existentes (R5).
-- MySQL no tiene ADD COLUMN IF NOT EXISTS: se consulta information_schema y se arma el ALTER.
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'nexus' AND TABLE_NAME = 'events' AND COLUMN_NAME = 'activity_id');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `nexus`.`events` ADD COLUMN `activity_id` BIGINT UNSIGNED NULL, ADD CONSTRAINT `fk_events_activity` FOREIGN KEY (`activity_id`) REFERENCES `nexus`.`activities` (`id`) ON DELETE SET NULL',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
