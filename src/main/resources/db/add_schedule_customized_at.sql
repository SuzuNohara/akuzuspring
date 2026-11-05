-- [primeros-pasos] R-03 (2026-11-05) - Hito «Ajusta tus horarios» de la tarjeta "Primeros pasos".
--   * schedule_customized_at: ultima vez que la persona guardo sus horarios (AvailabilityService
--     .saveUserSchedule). NULL = sigue con los de fabrica (trigger after_user_insert_create_default_schedule).
--   * Antes se adivinaba comparando users.created_at (reloj de la JVM) con availability_schedules.created_at
--     (reloj de MySQL) con 30 s de margen: fallaba con deriva de reloj o con la zona de la sesion (R-01, R-02).
--   * Relleno de usuarios existentes: backfill_schedule_customized_at.sql, despues de esta.
-- Idempotente para db-migrate: si la columna ya existe da 1060, que se tolera.

ALTER TABLE `nexus`.`users`
  ADD COLUMN `schedule_customized_at` DATETIME(3) NULL DEFAULT NULL AFTER `account_status`;
