-- [horario-3am] (2026-11-19) Horarios para citas guardados desde la app: pasar de "hora UTC" a hora local.
--
-- Antes del WallClockTimeConverter, Hibernate guardaba la hora del horario (TIME) sumandole 6 horas
-- (JVM en America/Mexico_City + hibernate.jdbc.time_zone=UTC): 09:00-22:00 quedaba como 15:00-04:00.
-- El trigger de registro, en cambio, escribe 09:00-22:00 tal cual, y por eso se leia como 03:00-16:00.
-- Desde el converter la hora se guarda y se lee sin moverla, asi que:
--   * los de fabrica (schedule_customized_at IS NULL) ya estan bien y no se tocan;
--   * los guardados desde la app (schedule_customized_at no nulo) se regresan 6 horas.
-- CDMX no tiene horario de verano desde 2022: el desfase siempre fue de 6 horas.
--
-- Correr UNA sola vez, junto con el despliegue del backend que trae el converter. Si se corre dos
-- veces, los horarios personalizados quedan 6 horas antes.

UPDATE `nexus`.`availability_schedules` a
  JOIN `nexus`.`users` u ON u.id = a.user_id
   SET a.start_time = SEC_TO_TIME(MOD(TIME_TO_SEC(a.start_time) - 21600 + 86400, 86400)),
       a.end_time   = SEC_TO_TIME(MOD(TIME_TO_SEC(a.end_time)   - 21600 + 86400, 86400))
 WHERE u.schedule_customized_at IS NOT NULL;
