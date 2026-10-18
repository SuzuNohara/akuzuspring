-- fix_catalogo_2026_10_18.sql - Arreglos de datos que salieron en la prueba en vivo (H14, H21, H23).
-- Idempotente: se puede correr mas de una vez. Antes de esto, volver a correr
-- seed_activities_profesional.sql (ya trae SET NAMES utf8mb4) para arreglar sus acentos.

SET NAMES utf8mb4;

-- H14: preferencia duplicada. "Paseos en parques" vive en Ambiental; la de Social quedo con un "1"
-- al cargarse. En Social se llama "Parques" (lugar favorito), sin chocar con la de Ambiental.
UPDATE `nexus`.`preferences` SET name = 'Parques' WHERE name = 'Paseos en parques 1';

-- H23: nombres de lugares tal como vienen de OpenStreetMap, con errores de captura.
UPDATE `nexus`.`places` SET name = 'Cardio Fitness Santa Fe' WHERE slug = 'alvaro-obregon-cardio-fitness-santa-nfe';
UPDATE `nexus`.`places` SET name = 'PILARES La Cañada' WHERE slug = 'la-magdalena-contreras-pilares-la-canada';
UPDATE `nexus`.`places` SET name = 'Máquinas de videojuegos' WHERE slug = 'alvaro-obregon-maquinas-de-videojuegos';
UPDATE `nexus`.`places` SET name = 'Máquinas de videojuegos' WHERE slug = 'alvaro-obregon-maquinas-de-videojuegos-sin-nombre';

-- H23: la "sala de escape" estaba ligada a maquinitas, salones de fiestas y una fonda. Ninguno es
-- una sala de escape: se quitan hasta tener el catalogo real (U12). Sin lugar, no se recomienda
-- fuera de casa (mejor ninguna sugerencia que una equivocada).
DELETE ap FROM `nexus`.`activity_places` ap
  JOIN `nexus`.`activities` a ON a.id = ap.activity_id
  WHERE a.slug = 'sala-de-escape';

-- H23: las clases de baile son actividad fisica, no intelectual (COURSE -> Intelectual).
UPDATE `nexus`.`activities` SET activity_type = 'SPORT_SESSION'
  WHERE slug IN ('clase-de-baile-de-salon', 'clase-de-baile-en-linea');

-- Comprobaciones
SELECT name FROM `nexus`.`preferences` WHERE name LIKE 'Parques' OR name LIKE 'Paseos en parques%';
SELECT title, description FROM `nexus`.`activities` WHERE activity_type = 'CAREER' AND (description LIKE '%├%' OR title LIKE '%├%');
SELECT COUNT(*) AS lugares_sala_de_escape FROM `nexus`.`activity_places` ap
  JOIN `nexus`.`activities` a ON a.id = ap.activity_id WHERE a.slug = 'sala-de-escape';
