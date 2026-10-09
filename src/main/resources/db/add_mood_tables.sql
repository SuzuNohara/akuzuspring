-- nexus-EMO-01 catalogo de las 8 opciones de CU31 con coordenadas del circumplejo de Russell (K3)
CREATE TABLE mood_options (
  id TINYINT UNSIGNED NOT NULL,
  code VARCHAR(32) NOT NULL,
  label VARCHAR(40) NOT NULL,
  emoji VARCHAR(16) NOT NULL,
  valence DECIMAL(3,2) NOT NULL,
  arousal DECIMAL(3,2) NOT NULL,
  sort_order TINYINT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mood_option_code (code),
  CONSTRAINT chk_mood_valence CHECK (valence BETWEEN -1 AND 1),
  CONSTRAINT chk_mood_arousal CHECK (arousal BETWEEN -1 AND 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- nexus-EMO-01 siembra PROVISIONAL (nombres por confirmar con el dev) re-aplicable: el fichero manda
INSERT INTO mood_options (id, code, label, emoji, valence, arousal, sort_order) VALUES
  (1, 'EXCITED',  'Emocionado',  '🤩',  0.71,  0.71, 1),
  (2, 'HAPPY',    'Feliz',       '😊',  1.00,  0.00, 2),
  (3, 'CALM',     'Tranquilo',   '😌',  0.71, -0.71, 3),
  (4, 'TIRED',    'Cansado',     '😴',  0.00, -1.00, 4),
  (5, 'SAD',      'Triste',      '😢', -0.71, -0.71, 5),
  (6, 'DOWN',     'Desanimado',  '😞', -1.00,  0.00, 6),
  (7, 'STRESSED', 'Estresado',   '😫', -0.71,  0.71, 7),
  (8, 'SURPRISED','Sorprendido', '😮',  0.00,  1.00, 8) AS new
ON DUPLICATE KEY UPDATE code = new.code, label = new.label, emoji = new.emoji,
  valence = new.valence, arousal = new.arousal, sort_order = new.sort_order;
-- nexus-EMO-01 registro diario (RF-31 RN-38): uno por usuario y dia natural de America/Mexico_City, opcion cifrada (RNF-11)
CREATE TABLE mood_entries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id INT UNSIGNED NOT NULL,
  entry_date DATE NOT NULL,
  mood_cipher VARBINARY(128) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mood_user_day (user_id, entry_date),
  CONSTRAINT fk_mood_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
