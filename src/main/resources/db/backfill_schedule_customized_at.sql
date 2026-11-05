-- [primeros-pasos] R-03 (2026-11-05) - Relleno de users.schedule_customized_at para cuentas existentes.
--   Marca a quien tiene horarios distintos de los de fabrica: los 7 dias, habilitados, 09:00-22:00
--   (trigger after_user_insert_create_default_schedule). La marca es el created_at mas reciente de sus
--   filas (saveUserSchedule las borra y las vuelve a crear).
--   Quien guardo exactamente el patron de fabrica queda pendiente: no se distingue del trigger.
-- Idempotente: solo toca filas con schedule_customized_at en NULL. Va despues de add_schedule_customized_at.sql.
UPDATE `nexus`.`users` u
  JOIN (
    SELECT a.user_id, MAX(a.created_at) AS saved_at
      FROM `nexus`.`availability_schedules` a
     GROUP BY a.user_id
    HAVING COUNT(*) <> 7
        OR SUM(a.is_enabled = 1 AND a.start_time = '09:00:00' AND a.end_time = '22:00:00') <> 7
  ) s ON s.user_id = u.id
   SET u.schedule_customized_at = s.saved_at
 WHERE u.schedule_customized_at IS NULL;
