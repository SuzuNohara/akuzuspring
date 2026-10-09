-- add_kb_tt_columns.sql (nexus-KB-02): columnas TT de activities/places, centroides y matriz de traslado (K1/K2/K4)
-- Categoria del banco (CU34) y dimension de bienestar (Tabla 37, K1): ambas NOT NULL; activities esta vacia al aplicar
ALTER TABLE activities ADD COLUMN bank_category VARCHAR(32) NOT NULL AFTER title;
ALTER TABLE activities ADD COLUMN wellness_dimension VARCHAR(16) NOT NULL AFTER bank_category;
-- Costo por pareja (K2): cost_max_mxn es el filtro de presupuesto; cost_mxn_pp heredada queda sin uso
ALTER TABLE activities ADD COLUMN cost_min_mxn INT UNSIGNED NOT NULL DEFAULT 0 AFTER cost_mxn_pp;
ALTER TABLE activities ADD COLUMN cost_max_mxn INT UNSIGNED NOT NULL DEFAULT 0 AFTER cost_min_mxn;
ALTER TABLE activities ADD COLUMN overrides VARCHAR(255) NOT NULL DEFAULT '' AFTER rationale;
ALTER TABLE activities ADD COLUMN canonical_text VARCHAR(1200) NOT NULL DEFAULT '' AFTER description;
ALTER TABLE activities ADD COLUMN canonical_hash CHAR(64) NULL AFTER canonical_text;
ALTER TABLE activities ADD COLUMN canonical_version TINYINT UNSIGNED NULL AFTER canonical_hash;
ALTER TABLE activities ADD INDEX idx_activities_bank (bank_category, title);
ALTER TABLE activities MODIFY difficulty_physical TINYINT UNSIGNED NOT NULL DEFAULT 0;
ALTER TABLE activities MODIFY difficulty_mental TINYINT UNSIGNED NOT NULL DEFAULT 0;
ALTER TABLE activities MODIFY collaboration TINYINT UNSIGNED NOT NULL DEFAULT 0;
ALTER TABLE activities MODIFY preparation TINYINT UNSIGNED NOT NULL DEFAULT 0;
ALTER TABLE places ADD COLUMN price_band VARCHAR(8) NULL AFTER weather_ok;
ALTER TABLE places ADD COLUMN canonical_text VARCHAR(1200) NOT NULL DEFAULT '' AFTER description;
ALTER TABLE places ADD COLUMN canonical_hash CHAR(64) NULL AFTER canonical_text;
ALTER TABLE places ADD COLUMN canonical_version TINYINT UNSIGNED NULL AFTER canonical_hash;
CREATE TABLE borough_centroids (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  borough VARCHAR(40) NOT NULL,
  lat DECIMAL(9,6) NOT NULL, lon DECIMAL(9,6) NOT NULL,
  source VARCHAR(60) NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_borough_centroids_borough (borough)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE transit_times (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  origin_borough VARCHAR(40) NOT NULL,
  dest_borough VARCHAR(40) NOT NULL,
  minutes_p50 SMALLINT UNSIGNED NOT NULL,
  minutes_p75 SMALLINT UNSIGNED NOT NULL,
  source VARCHAR(20) NOT NULL,
  n_trips INT UNSIGNED NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_transit_times_pair (origin_borough, dest_borough)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE OR REPLACE VIEW kb_records AS
SELECT a.slug AS activity_slug, a.title AS activity_title, p.slug AS place_slug, p.name AS place_name,
       a.bank_category, a.wellness_dimension, a.activity_type, a.location_scope,
       p.neighborhood, p.borough, p.lat, p.lon,
       a.price_band, p.price_band AS place_price_band, p.opening_hours,
       a.ambience, a.weather_ok, a.is_outdoor,
       a.duration_min, a.duration_avg, a.duration_max
FROM activities a
LEFT JOIN activity_places ap ON ap.activity_id = a.id
LEFT JOIN places p ON p.id = ap.place_id;
