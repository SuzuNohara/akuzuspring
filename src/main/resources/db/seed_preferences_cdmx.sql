-- Script para poblar las 7 Dimensiones del Bienestar adaptadas a CDMX
-- Ejecutar después de crear las tablas pref_categories y preferences

USE nexus_db;

-- Limpiar datos existentes si es necesario
-- DELETE FROM user_preferences;
-- DELETE FROM preferences;
-- DELETE FROM pref_categories;

-- ==============================================
-- 1. BIENESTAR FÍSICO 💪
-- ==============================================
INSERT INTO pref_categories (name, description) VALUES
('Bienestar Físico', 'Actividades físicas, deportes y ejercicio');

SET @physical_id = LAST_INSERT_ID();

INSERT INTO preferences (category_id, name, description) VALUES
-- Nivel de actividad
(@physical_id, 'gym', 'Gimnasio'),
(@physical_id, 'yoga', 'Yoga o Pilates'),
(@physical_id, 'running_cdmx', 'Correr (Bosque de Chapultepec, Viveros)'),
(@physical_id, 'cycling_cdmx', 'Ciclismo (Reforma, ciclovías)'),
(@physical_id, 'swimming', 'Natación'),
(@physical_id, 'dance', 'Baile (salsa, bachata, contemporáneo)'),
(@physical_id, 'soccer', 'Fútbol'),
(@physical_id, 'basketball', 'Basquetbol'),
(@physical_id, 'volleyball', 'Voleibol'),
(@physical_id, 'martial_arts', 'Artes marciales'),
(@physical_id, 'climbing', 'Escalada en roca (indoor)'),
(@physical_id, 'hiking_near_cdmx', 'Senderismo (Ajusco, Desierto de los Leones)');

-- ==============================================
-- 2. BIENESTAR EMOCIONAL 💖
-- ==============================================
INSERT INTO pref_categories (name, description) VALUES
('Bienestar Emocional', 'Lenguajes de amor, manejo de emociones y relación');

SET @emotional_id = LAST_INSERT_ID();

INSERT INTO preferences (category_id, name, description) VALUES
-- Lenguajes de amor
(@emotional_id, 'love_words', 'Palabras de afirmación'),
(@emotional_id, 'love_quality_time', 'Tiempo de calidad'),
(@emotional_id, 'love_gifts', 'Regalos'),
(@emotional_id, 'love_acts_service', 'Actos de servicio'),
(@emotional_id, 'love_physical_touch', 'Contacto físico'),
-- Manejo de estrés
(@emotional_id, 'stress_talk', 'Hablar del tema'),
(@emotional_id, 'stress_alone_time', 'Tiempo a solas'),
(@emotional_id, 'stress_music', 'Escuchar música'),
(@emotional_id, 'stress_exercise', 'Hacer ejercicio'),
(@emotional_id, 'stress_meditation', 'Meditación o mindfulness'),
(@emotional_id, 'stress_hobbies', 'Hacer mis hobbies');

-- ==============================================
-- 3. BIENESTAR SOCIAL 👥
-- ==============================================
INSERT INTO pref_categories (name, description) VALUES
('Bienestar Social', 'Actividades sociales y lugares favoritos en CDMX');

SET @social_id = LAST_INSERT_ID();

INSERT INTO preferences (category_id, name, description) VALUES
-- Actividades de cita
(@social_id, 'date_movies', 'Cine (Cineteca, Cinemex, Cinépolis)'),
(@social_id, 'date_dinner_cdmx', 'Cena romántica (Polanco, Roma, Condesa)'),
(@social_id, 'date_concerts', 'Conciertos (Auditorio, Foro Sol)'),
(@social_id, 'date_museums_cdmx', 'Museos (Antropología, Frida Kahlo, MUAC)'),
(@social_id, 'date_parks_cdmx', 'Parques (Chapultepec, España, México)'),
(@social_id, 'date_home', 'En casa (películas, cocinar juntos)'),
(@social_id, 'date_adventure_cdmx', 'Aventuras (Six Flags, Xochimilco)'),
(@social_id, 'date_games', 'Juegos de mesa o videojuegos'),
-- Lugares favoritos
(@social_id, 'venue_restaurants', 'Restaurantes'),
(@social_id, 'venue_cafes_cdmx', 'Cafés (Roma, Coyoacán, Centro)'),
(@social_id, 'venue_bars_cdmx', 'Bares y antros (Zona Rosa, Condesa)'),
(@social_id, 'venue_theaters_cdmx', 'Teatros (Bellas Artes, Insurgentes)'),
(@social_id, 'venue_malls_cdmx', 'Centros comerciales (Antara, Santa Fe)');

-- ==============================================
-- 4. BIENESTAR INTELECTUAL 🧠
-- ==============================================
INSERT INTO pref_categories (name, description) VALUES
('Bienestar Intelectual', 'Intereses culturales, hobbies y aprendizaje');

SET @intellectual_id = LAST_INSERT_ID();

INSERT INTO preferences (category_id, name, description) VALUES
-- Intereses
(@intellectual_id, 'interest_technology', 'Tecnología e innovación'),
(@intellectual_id, 'interest_science', 'Ciencia'),
(@intellectual_id, 'interest_art', 'Arte y diseño'),
(@intellectual_id, 'interest_history', 'Historia (especialmente de México)'),
(@intellectual_id, 'interest_politics', 'Política y actualidad'),
(@intellectual_id, 'interest_music', 'Música'),
(@intellectual_id, 'interest_literature', 'Literatura'),
(@intellectual_id, 'interest_philosophy', 'Filosofía'),
-- Hobbies
(@intellectual_id, 'hobby_reading', 'Leer'),
(@intellectual_id, 'hobby_writing', 'Escribir'),
(@intellectual_id, 'hobby_painting', 'Pintar o dibujar'),
(@intellectual_id, 'hobby_music_play', 'Tocar un instrumento'),
(@intellectual_id, 'hobby_photography', 'Fotografía'),
(@intellectual_id, 'hobby_cooking', 'Cocinar'),
(@intellectual_id, 'hobby_gaming', 'Videojuegos'),
(@intellectual_id, 'hobby_crafts', 'Manualidades o DIY');

-- ==============================================
-- 5. BIENESTAR PROFESIONAL 💼
-- ==============================================
INSERT INTO pref_categories (name, description) VALUES
('Bienestar Profesional', 'Trabajo, carrera y valores laborales');

SET @professional_id = LAST_INSERT_ID();

INSERT INTO preferences (category_id, name, description) VALUES
-- Estilo de apoyo
(@professional_id, 'support_encouragement', 'Palabras de ánimo'),
(@professional_id, 'support_advice', 'Dar consejos'),
(@professional_id, 'support_active_help', 'Ayuda activa'),
(@professional_id, 'support_space', 'Dar espacio'),
(@professional_id, 'support_celebration', 'Celebrar logros'),
-- Valores laborales
(@professional_id, 'work_value_growth', 'Crecimiento profesional'),
(@professional_id, 'work_value_stability', 'Estabilidad'),
(@professional_id, 'work_value_creativity', 'Creatividad'),
(@professional_id, 'work_value_impact', 'Impacto social'),
(@professional_id, 'work_value_income', 'Buenos ingresos'),
(@professional_id, 'work_value_flexibility', 'Flexibilidad'),
(@professional_id, 'work_value_passion', 'Seguir mi pasión');

-- ==============================================
-- 6. BIENESTAR AMBIENTAL 🌿
-- ==============================================
INSERT INTO pref_categories (name, description) VALUES
('Bienestar Ambiental', 'Conexión con la naturaleza y el entorno');

SET @environmental_id = LAST_INSERT_ID();

INSERT INTO preferences (category_id, name, description) VALUES
-- Actividades al aire libre
(@environmental_id, 'outdoor_hiking_cdmx', 'Senderismo (Ajusco, Tlalpan)'),
(@environmental_id, 'outdoor_parks_cdmx', 'Paseos por parques'),
(@environmental_id, 'outdoor_picnics', 'Picnics'),
(@environmental_id, 'outdoor_xochimilco', 'Xochimilco (trajineras)'),
(@environmental_id, 'outdoor_cycling', 'Ciclismo urbano'),
(@environmental_id, 'outdoor_botanical', 'Jardín Botánico UNAM'),
(@environmental_id, 'outdoor_daytrips', 'Escapadas (Teotihuacán, Tepoztlán, Valle de Bravo)'),
-- Preferencias ambientales
(@environmental_id, 'env_city_life', 'Vida urbana (CDMX)'),
(@environmental_id, 'env_nature_balance', 'Balance ciudad-naturaleza'),
(@environmental_id, 'env_eco_conscious', 'Consciencia ecológica');

-- ==============================================
-- 7. BIENESTAR ESPIRITUAL ✨
-- ==============================================
INSERT INTO pref_categories (name, description) VALUES
('Bienestar Espiritual', 'Valores, propósito y prácticas de bienestar');

SET @spiritual_id = LAST_INSERT_ID();

INSERT INTO preferences (category_id, name, description) VALUES
-- Valores fundamentales
(@spiritual_id, 'value_honesty', 'Honestidad'),
(@spiritual_id, 'value_loyalty', 'Lealtad'),
(@spiritual_id, 'value_growth', 'Crecimiento personal'),
(@spiritual_id, 'value_adventure', 'Aventura'),
(@spiritual_id, 'value_family', 'Familia'),
(@spiritual_id, 'value_freedom', 'Libertad'),
(@spiritual_id, 'value_kindness', 'Amabilidad'),
(@spiritual_id, 'value_ambition', 'Ambición'),
-- Prácticas de mindfulness
(@spiritual_id, 'mindfulness_meditation', 'Meditación'),
(@spiritual_id, 'mindfulness_yoga', 'Yoga'),
(@spiritual_id, 'mindfulness_prayer', 'Oración o reflexión espiritual'),
(@spiritual_id, 'mindfulness_journaling', 'Escribir diario'),
(@spiritual_id, 'mindfulness_nature', 'Tiempo en naturaleza'),
(@spiritual_id, 'mindfulness_art', 'Arte/creatividad'),
-- Actividades significativas
(@spiritual_id, 'meaningful_volunteering', 'Voluntariado'),
(@spiritual_id, 'meaningful_creating', 'Crear algo'),
(@spiritual_id, 'meaningful_helping', 'Ayudar a otros'),
(@spiritual_id, 'meaningful_learning', 'Aprender cosas nuevas'),
(@spiritual_id, 'meaningful_connecting', 'Conectar con otros'),
(@spiritual_id, 'meaningful_exploring', 'Explorar CDMX');

-- Verificar la inserción
SELECT 
    pc.name as categoria,
    COUNT(p.id) as total_preferencias
FROM pref_categories pc
LEFT JOIN preferences p ON pc.id = p.category_id
GROUP BY pc.id, pc.name
ORDER BY pc.id;
