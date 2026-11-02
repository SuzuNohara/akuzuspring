-- Borra los datos de prueba (cuentas y todo lo que crearon) para empezar las pruebas reales.
-- Se queda el catálogo: activities, activity_interests, activity_places, places, boroughs,
-- preferences, pref_categories, tags y las tablas de Liquibase. Qdrant solo guarda el
-- catálogo, así que no se toca.
--
-- Uso (cmd), DESPUÉS de sacar el respaldo con mysqldump:
--   mysql -u root -p nexus < scripts\limpiar_datos_prueba.sql
--
-- Una tabla de la lista que no exista en esta base se salta sin error.

USE nexus;

DROP PROCEDURE IF EXISTS limpiar_datos_prueba;
DELIMITER //
CREATE PROCEDURE limpiar_datos_prueba()
BEGIN
  DECLARE fin INT DEFAULT 0;
  DECLARE tabla VARCHAR(64);
  DECLARE cur CURSOR FOR
    SELECT table_name FROM information_schema.tables
     WHERE table_schema = 'nexus'
       AND table_name IN (
         'users', 'user_profile', 'user_preferences', 'user_roles', 'user_app_config',
         'user_links', 'link_codes', 'user_link_code_history', 'verification_tokens',
         'login_attempts', 'audit_logs', 'taggings',
         'events', 'event_exceptions', 'event_reminders',
         'calendars', 'calendar_events', 'calendar_event_exceptions', 'availability_schedules',
         'app_notifications', 'emotion_logs', 'date_ratings',
         'planning_sessions', 'planning_messages', 'planning_votes',
         'recommendation_requests', 'recommendations',
         'app_events', 'app_event_approvals', 'app_event_chat_log', 'app_event_chat_messages',
         'app_event_participants', 'app_event_photos', 'app_event_ratings');
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET fin = 1;

  SET FOREIGN_KEY_CHECKS = 0;
  OPEN cur;
  ciclo: LOOP
    FETCH cur INTO tabla;
    IF fin = 1 THEN LEAVE ciclo; END IF;
    SET @sql = CONCAT('TRUNCATE TABLE `nexus`.`', tabla, '`');
    PREPARE st FROM @sql;
    EXECUTE st;
    DEALLOCATE PREPARE st;
  END LOOP;
  CLOSE cur;
  SET FOREIGN_KEY_CHECKS = 1;
END //
DELIMITER ;

CALL limpiar_datos_prueba();
DROP PROCEDURE limpiar_datos_prueba;

-- Comprobación: cuentas en 0 y catálogo intacto.
SELECT 'users' AS tabla, COUNT(*) AS filas FROM users
UNION ALL SELECT 'events', COUNT(*) FROM events
UNION ALL SELECT 'user_links', COUNT(*) FROM user_links
UNION ALL SELECT 'activities (catalogo)', COUNT(*) FROM activities
UNION ALL SELECT 'places (catalogo)', COUNT(*) FROM places
UNION ALL SELECT 'preferences (catalogo)', COUNT(*) FROM preferences;
