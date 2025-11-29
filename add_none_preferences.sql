-- SQL para agregar la opción "Ninguna" a cada categoría de preferencias
-- Ejecutar este script en la base de datos Nexus

-- Obtener los IDs de las categorías
SET @physical_id = (SELECT id FROM preference_categories WHERE name = 'Bienestar Físico');
SET @emotional_id = (SELECT id FROM preference_categories WHERE name = 'Bienestar Emocional');
SET @social_id = (SELECT id FROM preference_categories WHERE name = 'Bienestar Social');
SET @intellectual_id = (SELECT id FROM preference_categories WHERE name = 'Bienestar Intelectual');
SET @professional_id = (SELECT id FROM preference_categories WHERE name = 'Bienestar Profesional');
SET @environmental_id = (SELECT id FROM preference_categories WHERE name = 'Bienestar Ambiental');
SET @spiritual_id = (SELECT id FROM preference_categories WHERE name = 'Bienestar Espiritual');

-- Insertar "Ninguna" para cada categoría
INSERT INTO preferences (category_id, name, description, created_at, updated_at) VALUES
(@physical_id, 'Ninguna de estas', 'No me identifico con ninguna de estas actividades físicas', NOW(), NOW()),
(@emotional_id, 'Ninguna de estas', 'No me identifico con ninguna de estas actividades emocionales', NOW(), NOW()),
(@social_id, 'Ninguna de estas', 'No me identifico con ninguna de estas actividades sociales', NOW(), NOW()),
(@intellectual_id, 'Ninguna de estas', 'No me identifico con ninguna de estas actividades intelectuales', NOW(), NOW()),
(@professional_id, 'Ninguna de estas', 'No me identifico con ninguna de estas actividades profesionales', NOW(), NOW()),
(@environmental_id, 'Ninguna de estas', 'No me identifico con ninguna de estas actividades ambientales', NOW(), NOW()),
(@spiritual_id, 'Ninguna de estas', 'No me identifico con ninguna de estas actividades espirituales', NOW(), NOW());

-- Verificar que se insertaron correctamente
SELECT pc.name AS categoria, p.name AS preferencia, p.description
FROM preferences p
INNER JOIN preference_categories pc ON p.category_id = pc.id
WHERE p.name = 'Ninguna de estas'
ORDER BY pc.id;
