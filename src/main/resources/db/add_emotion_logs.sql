-- add_emotion_logs.sql (RF-31/RF-32, RN-28, RNF-11)
-- Registro de estado emocional. valencia/activacion (modelo circunflejo, 2.7.1) y la etiqueta
-- van cifrados en encrypted_payload (AES-256-GCM, AesEncryptionService) como un solo JSON --
-- RN-28 los hace privados (ni la pareja los ve), RNF-11 exige cifrado en reposo.

CREATE TABLE emotion_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  encrypted_payload VARCHAR(512) NOT NULL,
  logged_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_emotion_logs_user_logged_at (user_id, logged_at),
  CONSTRAINT fk_emotion_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
