-- nexus-SCHEMA-02 id del evento en el dispositivo, clave de deduplicacion de syncEvents
ALTER TABLE calendar_events ADD COLUMN device_event_id VARCHAR(255) NULL DEFAULT NULL AFTER calendar_id;
-- nexus-SCHEMA-02 lugar del evento importado (se enmascara con BUSY_ONLY)
ALTER TABLE calendar_events ADD COLUMN location VARCHAR(255) NULL DEFAULT NULL AFTER rrule_count;
-- nexus-SCHEMA-02 descripcion del evento importado (se enmascara con BUSY_ONLY)
ALTER TABLE calendar_events ADD COLUMN description TEXT NULL AFTER location;
-- nexus-SCHEMA-02 RN-20 marca de solo lectura de los eventos importados
ALTER TABLE calendar_events ADD COLUMN is_external TINYINT(1) NOT NULL DEFAULT 0 AFTER description;
-- nexus-SCHEMA-02 RF-24 marca de tiempo del dispositivo para detectar modificaciones externas
ALTER TABLE calendar_events ADD COLUMN last_device_update DATETIME(3) NULL DEFAULT NULL AFTER status;
-- nexus-SCHEMA-02 SHA-256 en Base64 (44 caracteres) del contenido sincronizado
ALTER TABLE calendar_events ADD COLUMN sync_hash VARCHAR(64) NULL DEFAULT NULL AFTER last_device_update;
-- nexus-SCHEMA-02 @Enumerated(STRING) escribe DEFAULT PUBLIC PRIVATE y el ENUM en minusculas rompe valueOf al leer
ALTER TABLE calendar_events MODIFY COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'DEFAULT';
-- nexus-SCHEMA-02 @Enumerated(STRING) escribe CONFIRMED TENTATIVE CANCELLED
ALTER TABLE calendar_events MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';
-- nexus-SCHEMA-02 normaliza filas previas a los literales de los enums de ExternalEvent
UPDATE calendar_events SET visibility = UPPER(visibility), status = UPPER(status) WHERE id > 0;
-- nexus-SCHEMA-02 columna generada 1 si la fila esta viva y NULL si esta borrada en suave, para que la clave unica ignore las borradas
ALTER TABLE calendar_events ADD COLUMN is_live TINYINT(1) GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED AFTER deleted_at;
-- nexus-SCHEMA-02 dos sincronizaciones concurrentes no pueden duplicar un evento vivo y la clave sirve de indice a findByExternalCalendarIdAndDeviceEventId
ALTER TABLE calendar_events ADD UNIQUE INDEX uk_ce_calendar_device_live (calendar_id, device_event_id, is_live);
