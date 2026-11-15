-- juegos_especificos_2026_11_15.sql - [juegos-especificos] «Juegos» se divide en tres (prueba con usuarios del 5-nov).
-- Para bases que ya existen (AWS o una local vieja); las nuevas lo traen en deploy/db/init.sql.
-- Idempotente: se puede correr mas de una vez. Quien ya habia elegido «Juegos» queda con «Juegos de mesa».

SET NAMES utf8mb4;

UPDATE `nexus`.`preferences` SET name = 'Juegos de mesa', description = 'Juegos de mesa juntos'
  WHERE category_id = 3 AND name = 'Juegos';

INSERT INTO `nexus`.`preferences` (category_id, name, description)
SELECT 3, 'Videojuegos', 'Videojuegos juntos' FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM `nexus`.`preferences` WHERE category_id = 3 AND name = 'Videojuegos');

INSERT INTO `nexus`.`preferences` (category_id, name, description)
SELECT 3, 'Boliche y arcade', 'Jugar boliche o maquinitas juntos' FROM DUAL
  WHERE NOT EXISTS (SELECT 1 FROM `nexus`.`preferences` WHERE category_id = 3 AND name = 'Boliche y arcade');

-- Comprobacion: deben salir las tres
SELECT id, name FROM `nexus`.`preferences` WHERE category_id = 3 AND name IN ('Juegos de mesa', 'Videojuegos', 'Boliche y arcade');
