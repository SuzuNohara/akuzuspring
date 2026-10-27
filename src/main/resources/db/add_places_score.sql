-- add_places_score.sql ([kb-lugares] U1) -- escrito a mano.
-- places.score: popularidad del lugar (place_anchors.csv de kb-final). Ordena los lugares de cada
-- actividad: tope de 10 por alcaldia en Qdrant (U6), desempate y "Sin zona" (U7). NULL = sin dato.
-- MySQL no tiene ADD COLUMN IF NOT EXISTS: se consulta information_schema y se arma el ALTER.
-- Idempotente: se puede correr varias veces. Requiere add_kb_tables.sql.

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'nexus' AND TABLE_NAME = 'places' AND COLUMN_NAME = 'score');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `nexus`.`places` ADD COLUMN `score` DECIMAL(7,3) NULL AFTER `needs_review`, ADD KEY `idx_places_score` (`score`)',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Comprobacion: 1 columna score.
SELECT COUNT(*) AS score FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'nexus' AND TABLE_NAME = 'places' AND COLUMN_NAME = 'score';
