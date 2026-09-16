-- nexus-SCHEMA-02 id del calendario en el dispositivo (expo-calendar), clave de busqueda de linkCalendar
ALTER TABLE calendars ADD COLUMN device_calendar_id VARCHAR(255) NULL DEFAULT NULL AFTER external_id;
-- nexus-SCHEMA-02 RF-22 el usuario decide si sincroniza este calendario
ALTER TABLE calendars ADD COLUMN sync_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER color_hex;
-- nexus-SCHEMA-02 RF-23 y RN-19 privacidad por defecto restrictiva
ALTER TABLE calendars ADD COLUMN privacy_mode VARCHAR(20) NOT NULL DEFAULT 'BUSY_ONLY' AFTER sync_enabled;
-- nexus-SCHEMA-02 ultima sincronizacion correcta
ALTER TABLE calendars ADD COLUMN last_sync DATETIME(3) NULL DEFAULT NULL AFTER privacy_mode;
-- nexus-SCHEMA-02 RF-26 desvinculacion reversible (unlinkCalendar desactiva, linkCalendar reactiva)
ALTER TABLE calendars ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1 AFTER last_sync;
-- nexus-SCHEMA-02 @Enumerated(STRING) escribe LOCAL GOOGLE OUTLOOK y el ENUM en minusculas rompe valueOf al leer
ALTER TABLE calendars MODIFY COLUMN source VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
-- nexus-SCHEMA-02 normaliza filas previas al literal que espera CalendarSource.valueOf
UPDATE calendars SET source = UPPER(source) WHERE id > 0;
-- nexus-SCHEMA-02 filtro exacto de findByUserIdAndIsActiveTrue
ALTER TABLE calendars ADD INDEX idx_cal_owner_active (owner_user_id, is_active, deleted_at);
