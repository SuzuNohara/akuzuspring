-- add_emotion_logs.sql (RF-31/RF-32, RN-28, RNF-11)
--
-- emotion_logs YA EXISTE en la base real, con un diseno categorico distinto (emotion_code +
-- intensity + notes, sin cifrar) -- probablemente de una planeacion previa de nexussync/NOVA,
-- nunca conectado a ningun codigo Java. Se decidio (2026-10-06) quedarse con el modelo
-- circunflejo (2.7.1: valencia + activacion continuas) ya documentado en el Marco Teorico, asi
-- que esto es un ALTER sobre la tabla real, no un CREATE.
--
-- valencia, activacion y la etiqueta van juntas como un solo JSON cifrado en encrypted_payload
-- (AES-256-GCM, AesEncryptionService) -- RN-28 los hace privados (ni la pareja los ve), RNF-11
-- exige cifrado en reposo. Se reutiliza la columna created_at ya existente para la fecha de
-- registro, en vez de anadir una columna logged_at nueva.

-- idx_em_user_time usa occurred_at, que se elimina -- hay que tumbar el indice antes de poder
-- soltar la columna, y se repone apuntando a created_at (misma intencion: historial por fecha).
ALTER TABLE `nexus`.`emotion_logs`
  DROP INDEX `idx_em_user_time`;

ALTER TABLE `nexus`.`emotion_logs`
  ADD COLUMN `encrypted_payload` VARCHAR(512) NOT NULL AFTER `user_id`,
  DROP COLUMN `emotion_code`,
  DROP COLUMN `intensity`,
  DROP COLUMN `notes`,
  DROP COLUMN `occurred_at`,
  ADD INDEX `idx_emotion_logs_user_created` (`user_id`, `created_at`);
