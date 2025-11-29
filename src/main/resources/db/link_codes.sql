-- Tabla de códigos temporales de vinculación (CU08)
-- RN-09: El código expira en 15 minutos y solo puede usarse una vez
CREATE TABLE IF NOT EXISTS link_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(8) NOT NULL UNIQUE,
    generated_by_user_id INT UNSIGNED NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_by_user_id INT UNSIGNED NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_linkcode_generator FOREIGN KEY (generated_by_user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_code (code),
    INDEX idx_generator_active (generated_by_user_id, is_used, expires_at),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
