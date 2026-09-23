-- add_kb_tables.sql (nexus-IDEAS-01): places, activities, activity_interests, activity_places, indices dentro de cada CREATE

CREATE TABLE places (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  slug VARCHAR(120) NOT NULL,
  name VARCHAR(160) NOT NULL,
  osm_type VARCHAR(8) NULL, osm_id BIGINT NULL,
  family VARCHAR(16) NOT NULL, place_type VARCHAR(32) NOT NULL,
  lat DECIMAL(9,6) NOT NULL, lon DECIMAL(9,6) NOT NULL,
  address VARCHAR(255) NULL, neighborhood VARCHAR(120) NULL, borough VARCHAR(40) NOT NULL,
  opening_hours VARCHAR(255) NULL, website VARCHAR(255) NULL, phone VARCHAR(40) NULL,
  is_outdoor TINYINT(1) NOT NULL DEFAULT 0, weather_ok VARCHAR(40) NOT NULL DEFAULT 'ANY',
  ambience VARCHAR(80) NOT NULL DEFAULT '', description VARCHAR(400) NOT NULL DEFAULT '',
  source VARCHAR(20) NOT NULL, license VARCHAR(20) NOT NULL,
  verified_by VARCHAR(60) NULL, verified_at DATETIME(3) NULL, needs_review TINYINT(1) NOT NULL DEFAULT 1,
  embedding JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_places_slug (slug),
  KEY idx_places_borough (borough), KEY idx_places_type (place_type), KEY idx_places_latlon (lat, lon)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE activities (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  slug VARCHAR(160) NOT NULL, title VARCHAR(120) NOT NULL,
  activity_type VARCHAR(32) NOT NULL, location_scope VARCHAR(8) NOT NULL,
  place_types VARCHAR(255) NOT NULL DEFAULT '',
  dayparts VARCHAR(32) NOT NULL, seasons VARCHAR(160) NOT NULL DEFAULT 'ANY',
  duration_min SMALLINT UNSIGNED NOT NULL, duration_avg SMALLINT UNSIGNED NOT NULL, duration_max SMALLINT UNSIGNED NOT NULL,
  difficulty_physical TINYINT UNSIGNED NOT NULL, difficulty_mental TINYINT UNSIGNED NOT NULL,
  collaboration TINYINT UNSIGNED NOT NULL, preparation TINYINT UNSIGNED NOT NULL,
  requires_booking TINYINT(1) NOT NULL DEFAULT 0, equipment VARCHAR(255) NOT NULL DEFAULT '',
  cost_mxn_pp INT UNSIGNED NOT NULL DEFAULT 0, price_band VARCHAR(8) NOT NULL,
  is_outdoor TINYINT(1) NOT NULL DEFAULT 0, weather_ok VARCHAR(40) NOT NULL DEFAULT 'ANY',
  ambience VARCHAR(80) NOT NULL DEFAULT '', description VARCHAR(400) NOT NULL DEFAULT '',
  rationale VARCHAR(300) NOT NULL DEFAULT '',
  enriched_by VARCHAR(40) NULL, enriched_at DATETIME(3) NULL, confidence DECIMAL(3,2) NULL,
  reviewed_by VARCHAR(60) NULL, needs_review TINYINT(1) NOT NULL DEFAULT 1,
  embedding JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_activities_slug (slug),
  KEY idx_activities_scope (location_scope), KEY idx_activities_type (activity_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE activity_interests (
  activity_id BIGINT UNSIGNED NOT NULL, preference_id INT UNSIGNED NOT NULL,
  PRIMARY KEY (activity_id, preference_id),
  CONSTRAINT fk_ai_activity FOREIGN KEY (activity_id) REFERENCES activities (id) ON DELETE CASCADE,
  CONSTRAINT fk_ai_preference FOREIGN KEY (preference_id) REFERENCES preferences (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE activity_places (
  activity_id BIGINT UNSIGNED NOT NULL, place_id BIGINT UNSIGNED NOT NULL,
  cost_override INT UNSIGNED NULL, notes VARCHAR(200) NOT NULL DEFAULT '', source VARCHAR(20) NOT NULL,
  PRIMARY KEY (activity_id, place_id),
  CONSTRAINT fk_ap_activity FOREIGN KEY (activity_id) REFERENCES activities (id) ON DELETE CASCADE,
  CONSTRAINT fk_ap_place FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
