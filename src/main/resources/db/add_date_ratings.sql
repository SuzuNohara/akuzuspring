-- CU42 (2026-10-19) - Calificar la satisfaccion de una cita (RF-41, RN-36, RN-37, RNF-11).
--   * Una fila por persona y por evento (unica): se califica una sola vez y no se edita.
--   * encrypted_score: el puntaje 1-5 cifrado con AES-256-GCM (AesEncryptionService), como
--     emotion_logs; nunca en claro. Es privado: la pareja no lo ve (RN-37).
--   * Sin llaves foraneas, igual que planning_sessions, para no chocar con los tipos de las
--     tablas viejas; la pertenencia la revisa RatingService.
-- Idempotente: se puede correr varias veces.

CREATE TABLE IF NOT EXISTS `nexus`.`date_ratings` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `event_id` BIGINT UNSIGNED NOT NULL,
  `user_id` INT UNSIGNED NOT NULL,
  `encrypted_score` VARCHAR(255) NOT NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_date_ratings_event_user` (`event_id`, `user_id`),
  KEY `idx_date_ratings_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
