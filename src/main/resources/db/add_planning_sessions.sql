-- T-06 ola 6' (2026-10-16) - Planeacion en pareja (RF-38, RF-39; RN-34, RN-39, RN-40 nuevas).
--   * planning_sessions: invitacion de 24 h, sesion de 12 h desde que la pareja acepta, filtros
--     compartidos, "Listo" de cada quien, solicitud de opciones vigente, propuesta y evento final.
--     No guarda la ubicacion de nadie: "Cerca de nosotros" vive en memoria del servidor (RN-30).
--   * planning_votes: "Me gusta" / "No me convence" de cada quien por opcion.
--   * planning_messages: chat de la sesion; se borra al cerrarla (acuerdo o fin de las 12 h).
--   * app_notifications: avisos dentro de la app (B9) mientras no haya notificaciones push.
--   * recommendation_requests.category admite varias dimensiones separadas por coma.
-- Idempotente: se puede correr varias veces. Requiere add_recommendation_tables.sql y
-- alter_recommendations_v2.sql.

CREATE TABLE IF NOT EXISTS `nexus`.`planning_sessions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `link_id` BIGINT UNSIGNED NOT NULL,
  `inviter_user_id` INT UNSIGNED NOT NULL,
  `partner_user_id` INT UNSIGNED NOT NULL,
  `status` VARCHAR(12) NOT NULL,                 -- INVITED | ACTIVE | AGREED | EXPIRED | CLOSED | CANCELLED
  `invite_expires_at` DATETIME(6) NOT NULL,
  `accepted_at` DATETIME(6) NULL,
  `session_expires_at` DATETIME(6) NULL,
  `closed_at` DATETIME(6) NULL,
  `zone_type` VARCHAR(8) NULL,
  `borough_id` TINYINT UNSIGNED NULL,
  `scope` VARCHAR(4) NULL,
  `max_cost_mxn` INT UNSIGNED NULL,
  `categories` VARCHAR(120) NULL,
  `slot_start` DATETIME(6) NULL,
  `slot_end` DATETIME(6) NULL,
  `inviter_ready` TINYINT(1) NOT NULL DEFAULT 0,
  `partner_ready` TINYINT(1) NOT NULL DEFAULT 0,
  `adjusting_user_id` INT UNSIGNED NULL,
  `request_id` BIGINT UNSIGNED NULL,
  `proposal_recommendation_id` BIGINT UNSIGNED NULL,
  `proposed_by_user_id` INT UNSIGNED NULL,
  `event_id` BIGINT UNSIGNED NULL,
  `inviter_seen_at` DATETIME(6) NULL,
  `partner_seen_at` DATETIME(6) NULL,
  `inviter_chat_seen_at` DATETIME(6) NULL,
  `partner_chat_seen_at` DATETIME(6) NULL,
  `reminders_sent` VARCHAR(40) NOT NULL DEFAULT '',
  `version` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `ix_planning_sessions_link` (`link_id`, `status`),
  KEY `ix_planning_sessions_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `nexus`.`planning_votes` (
  `recommendation_id` BIGINT UNSIGNED NOT NULL,
  `user_id` INT UNSIGNED NOT NULL,
  `session_id` BIGINT UNSIGNED NOT NULL,
  `vote` VARCHAR(8) NOT NULL,                    -- LIKE | DISLIKE
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`recommendation_id`, `user_id`),
  KEY `ix_planning_votes_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `nexus`.`planning_messages` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `session_id` BIGINT UNSIGNED NOT NULL,
  `sender_user_id` INT UNSIGNED NOT NULL,
  `body` VARCHAR(500) NOT NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `ix_planning_messages_session` (`session_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `nexus`.`app_notifications` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT UNSIGNED NOT NULL,
  `session_id` BIGINT UNSIGNED NULL,
  `type` VARCHAR(24) NOT NULL,
  `title` VARCHAR(120) NOT NULL,
  `body` VARCHAR(200) NOT NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `read_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `ix_app_notifications_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `nexus`.`recommendation_requests` MODIFY COLUMN `category` VARCHAR(120) NULL;
