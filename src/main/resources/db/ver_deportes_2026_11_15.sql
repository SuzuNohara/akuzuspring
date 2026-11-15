-- ver_deportes_2026_11_15.sql - [ver-deportes] «Ver deportes» en Intelectual › Pasatiempos (prueba con usuarios del 5-nov).
-- Practicar deporte ya esta en Bienestar Fisico; esta es la de seguir partidos o ir al estadio.
-- Para bases que ya existen (AWS o una local vieja); las nuevas lo traen en deploy/db/init.sql. Idempotente.

SET NAMES utf8mb4;

INSERT INTO `nexus`.`preferences` (category_id, name, description)
SELECT 4, 'Ver Deportes', 'Seguir partidos o ir al estadio' FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM `nexus`.`preferences` WHERE category_id = 4 AND name = 'Ver Deportes');

-- Comprobacion: debe salir una fila
SELECT id, name FROM `nexus`.`preferences` WHERE category_id = 4 AND name = 'Ver Deportes';
