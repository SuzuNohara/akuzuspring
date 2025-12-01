-- Tabla para almacenar calendarios externos vinculados desde el dispositivo
-- Adaptada para expo-calendar (sin OAuth, usa device_calendar_id)
CREATE TABLE IF NOT EXISTS external_calendars (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_calendar_id VARCHAR(255) NOT NULL COMMENT 'ID del calendario en el dispositivo',
    calendar_name VARCHAR(255) NOT NULL,
    calendar_source VARCHAR(100) COMMENT 'Google, Outlook, iCloud, Local, etc.',
    calendar_color VARCHAR(20),
    sync_enabled BOOLEAN DEFAULT TRUE COMMENT 'Si la sincronización está activa',
    privacy_mode ENUM('FULL_DETAILS', 'BUSY_ONLY') DEFAULT 'BUSY_ONLY' COMMENT 'RF-23: Privacidad',
    last_sync TIMESTAMP NULL COMMENT 'Última sincronización exitosa',
    is_active BOOLEAN DEFAULT TRUE COMMENT 'Para soft delete (RF-26)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_user_calendar (user_id, device_calendar_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_active (user_id, is_active),
    INDEX idx_user_sync (user_id, sync_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla para almacenar eventos importados desde calendarios externos
CREATE TABLE IF NOT EXISTS external_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    external_calendar_id BIGINT NOT NULL,
    device_event_id VARCHAR(255) NOT NULL COMMENT 'ID del evento en el dispositivo',
    title VARCHAR(255) NOT NULL,
    start_datetime TIMESTAMP NOT NULL,
    end_datetime TIMESTAMP NOT NULL,
    location VARCHAR(255),
    description TEXT,
    is_all_day BOOLEAN DEFAULT FALSE,
    recurrence_rule VARCHAR(500) COMMENT 'Regla de recurrencia RRULE',
    visibility ENUM('FULL_DETAILS', 'BUSY_ONLY') NOT NULL DEFAULT 'FULL_DETAILS',
    last_device_update TIMESTAMP NULL COMMENT 'RF-24: Detectar modificaciones externas',
    sync_hash VARCHAR(64) COMMENT 'Hash para detectar cambios',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_calendar_event (external_calendar_id, device_event_id),
    FOREIGN KEY (external_calendar_id) REFERENCES external_calendars(id) ON DELETE CASCADE,
    INDEX idx_calendar_dates (external_calendar_id, start_datetime, end_datetime),
    INDEX idx_datetime_range (start_datetime, end_datetime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla para almacenar notificaciones de disponibilidad mutua (RF-29)
CREATE TABLE IF NOT EXISTS mutual_availability_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user1_id BIGINT NOT NULL,
    user2_id BIGINT NOT NULL,
    slot_start TIMESTAMP NOT NULL,
    slot_end TIMESTAMP NOT NULL,
    duration_minutes INT NOT NULL,
    notified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notification_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user1_unread (user1_id, notification_read),
    INDEX idx_user2_unread (user2_id, notification_read),
    INDEX idx_slot_time (slot_start, slot_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
