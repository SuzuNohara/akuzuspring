-- Anade la opcion "Ninguna de estas" a cada una de las 7 dimensiones de bienestar.
--
-- Idempotente: re-ejecutarlo no duplica filas. Se apoya en la clave unica
-- uk_pref_cat_name (category_id, name) de `preferences` (Nexus.sql:580).
--
-- La tabla de categorias es `pref_categories` (Nexus.sql:552). La version anterior
-- de este fichero consultaba `preference_categories`, que no existe en ningun sitio
-- del esquema: sobre base virgen daba ERROR 1146 y abortaba db-seed-force a mitad,
-- despues de que los tres seeds previos ya hubieran ejecutado sus DELETE.
--
-- Sin variables de sesion: el JOIN contra pref_categories resuelve los ids. Una
-- categoria ausente simplemente no produce fila, en vez de propagar un NULL a una
-- columna NOT NULL y fallar con 1048.

INSERT INTO preferences (category_id, name, description, created_at, updated_at)
SELECT pc.id,
       'Ninguna de estas',
       d.descripcion,
       NOW(),
       NOW()
FROM pref_categories pc
JOIN (
          SELECT 'Bienestar Físico'       AS categoria, 'No me identifico con ninguna de estas actividades físicas'       AS descripcion
    UNION SELECT 'Bienestar Emocional',                 'No me identifico con ninguna de estas actividades emocionales'
    UNION SELECT 'Bienestar Social',                    'No me identifico con ninguna de estas actividades sociales'
    UNION SELECT 'Bienestar Intelectual',               'No me identifico con ninguna de estas actividades intelectuales'
    UNION SELECT 'Bienestar Profesional',               'No me identifico con ninguna de estas actividades profesionales'
    UNION SELECT 'Bienestar Ambiental',                 'No me identifico con ninguna de estas actividades ambientales'
    UNION SELECT 'Bienestar Espiritual',                'No me identifico con ninguna de estas actividades espirituales'
) d ON d.categoria = pc.name
WHERE pc.deleted_at IS NULL
ON DUPLICATE KEY UPDATE preferences.id = preferences.id;
