-- Script para poblar las 7 Dimensiones del Bienestar (versión limpia sin lugares específicos)
-- Ejecutar después de limpiar o crear las tablas

USE nexus_db;

-- Limpiar datos existentes
DELETE FROM user_preferences;
DELETE FROM preferences;
DELETE FROM pref_categories;

-- Resetear auto_increment
ALTER TABLE pref_categories AUTO_INCREMENT = 1;
ALTER TABLE preferences AUTO_INCREMENT = 1;

-- ==============================================
-- 1. BIENESTAR FÍSICO 💪
-- ==============================================
INSERT INTO pref_categories (name, description) VALUES
('Bienestar Físico', 'Actividades físicas, deportes y ejercicio');

SET @physical_id = LAST_INSERT_ID();

INSERT INTO preferences (category_id, name, description) VALUES
(@physical_id, 'Gimnasio', 'Entrenamiento con pesas, máquinas y ejercicio funcional'),
(@physical_id, 'Yoga', 'Práctica de yoga, pilates y estiramientos'),
(@physical_id, 'Correr', 'Running y jogging'),
(@physical_id, 'Ciclismo', 'Andar en bicicleta y ciclovías'),
(@physical_id, 'Natación', 'Nadar en alberca'),
(@physical_id, 'Baile', 'Salsa, bachata, contemporáneo y otros estilos'),
(@physical_id, 'Fútbol', 'Jugar fútbol'),
(@physical_id, 'Basquetbol', 'Jugar básquet'),
(@physical_id, 'Voleibol', 'Jugar voleibol'),
(@physical_id, 'Artes Marciales', 'Karate, judo, taekwondo, etc.'),
(@physical_id, 'Escalada', 'Escalada en roca (indoor/outdoor)'),
(@physical_id, 'Senderismo', 'Caminatas y excursiones en la naturaleza');

-- ==============================================
-- 2. BIENESTAR EMOCIONAL 💖
-- ==============================================
INSERT INTO pref_categories (name, description) VALUES
('Bienestar Emocional', 'Lenguajes de amor, manejo de emociones y relación');

SET @emotional_id = LAST_INSERT_ID();

INSERT INTO preferences (category_id, name, description) VALUES
-- Lenguajes de amor
(@emotional_id, 'Palabras de Afirmación', 'Expresar amor con palabras y cumplidos'),
(@emotional_id, 'Tiempo de Calidad', 'Pasar tiempo juntos sin distracciones'),
(@emotional_id, 'Regalos', 'Dar y recibir detalles significativos'),
(@emotional_id, 'Actos de Servicio', 'Demostrar amor haciendo cosas por el otro'),
(@emotional_id, 'Contacto Físico', 'Expresar amor con abrazos, besos y caricias'),
-- Manejo de estrés
(@emotional_id, 'Hablar del Tema', 'Conversar sobre lo que causa estrés'),
(@emotional_id, 'Tiempo a Solas', 'Tener espacio personal para procesar'),
(@emotional_id, 'Escuchar Música', 'Relajarse con música'),
(@emotional_id, 'Hacer Ejercicio', 'Liberar estrés con actividad física'),
(@emotional_id, 'Meditación', 'Mindfulness y técnicas de relajación'),
(@emotional_id, 'Hacer Hobbies', 'Dedicar tiempo a pasatiempos favoritos');

-- ==============================================
-- 3. BIENESTAR SOCIAL 👥
-- ==============================================
INSERT INTO pref_categories (name, description) VALUES
('Bienestar Social', 'Actividades sociales y lugares favoritos');

SET @social_id = LAST_INSERT_ID();

INSERT INTO preferences (category_id, name, description) VALUES
-- Actividades de cita
(@social_id, 'Ir al Cine', 'Ver películas en sala'),
(@social_id, 'Cena Romántica', 'Salir a cenar a restaurantes'),
(@social_id, 'Conciertos', 'Asistir a conciertos y shows en vivo'),
(@social_id, 'Visitar Museos', 'Explorar museos y galerías de arte'),
(@social_id, 'Paseos en Parques', 'Caminar y pasar tiempo en parques'),
(@social_id, 'Quedarse en Casa', 'Ver películas o cocinar juntos en casa'),
(@social_id, 'Parques de Diversiones', 'Ir a parques temáticos y de aventura'),
(@social_id, 'Juegos', 'Juegos de mesa o videojuegos juntos'),
-- Lugares favoritos
(@social_id, 'Restaurantes', 'Explorar diferentes tipos de cocina'),
(@social_id, 'Cafeterías', 'Tomar café y conversar'),
(@social_id, 'Bares y Antros', 'Salir a bailar y vida nocturna'),
(@social_id, 'Teatro', 'Ver obras de teatro y musicales'),
(@social_id, 'Centros Comerciales', 'Ir de compras juntos');

-- ==============================================
-- 4. BIENESTAR INTELECTUAL 🧠
-- ==============================================
INSERT INTO pref_categories (name, description) VALUES
('Bienestar Intelectual', 'Intereses culturales, hobbies y aprendizaje');

SET @intellectual_id = LAST_INSERT_ID();

INSERT INTO preferences (category_id, name, description) VALUES
-- Intereses
(@intellectual_id, 'Tecnología', 'Interés en tecnología e innovación'),
(@intellectual_id, 'Ciencia', 'Curiosidad por temas científicos'),
(@intellectual_id, 'Arte y Diseño', 'Apreciación del arte visual'),
(@intellectual_id, 'Historia', 'Interés en historia y cultura'),
(@intellectual_id, 'Política y Actualidad', 'Seguir noticias y eventos actuales'),
(@intellectual_id, 'Música', 'Descubrir y disfrutar diferentes géneros'),
(@intellectual_id, 'Literatura', 'Leer y discutir libros'),
(@intellectual_id, 'Filosofía', 'Reflexionar sobre temas existenciales'),
-- Hobbies
(@intellectual_id, 'Leer', 'Lectura de libros, artículos y más'),
(@intellectual_id, 'Escribir', 'Escritura creativa o personal'),
(@intellectual_id, 'Pintar o Dibujar', 'Artes visuales y dibujo'),
(@intellectual_id, 'Tocar Instrumento', 'Tocar guitarra, piano u otro instrumento'),
(@intellectual_id, 'Fotografía', 'Tomar fotos y editar imágenes'),
(@intellectual_id, 'Cocinar', 'Preparar recetas nuevas y experimentar'),
(@intellectual_id, 'Videojuegos', 'Jugar videojuegos solo o en línea'),
(@intellectual_id, 'Manualidades', 'Proyectos DIY y trabajos manuales');

-- ==============================================
-- 5. BIENESTAR PROFESIONAL 💼
-- ==============================================
INSERT INTO pref_categories (name, description) VALUES
('Bienestar Profesional', 'Apoyo en el trabajo y carrera');

SET @professional_id = LAST_INSERT_ID();

INSERT INTO preferences (category_id, name, description) VALUES
-- Estilo de apoyo
(@professional_id, 'Dar Palabras de Ánimo', 'Motivar y alentar con palabras'),
(@professional_id, 'Dar Consejos', 'Ofrecer soluciones y perspectivas'),
(@professional_id, 'Ayuda Práctica', 'Ayudar activamente en tareas'),
(@professional_id, 'Dar Espacio', 'Respetar tiempo personal'),
(@professional_id, 'Celebrar Logros', 'Reconocer y festejar éxitos');

-- ==============================================
-- 6. BIENESTAR AMBIENTAL 🌿
-- ==============================================
INSERT INTO pref_categories (name, description) VALUES
('Bienestar Ambiental', 'Conexión con la naturaleza y el entorno');

SET @environmental_id = LAST_INSERT_ID();

INSERT INTO preferences (category_id, name, description) VALUES
-- Actividades al aire libre
(@environmental_id, 'Senderismo en Naturaleza', 'Caminatas en montañas y bosques'),
(@environmental_id, 'Paseos en Parques', 'Disfrutar áreas verdes urbanas'),
(@environmental_id, 'Picnics', 'Comer al aire libre'),
(@environmental_id, 'Paseos en Trajinera', 'Navegar en canales y lagos'),
(@environmental_id, 'Ciclismo al Aire Libre', 'Rutas en bicicleta por la ciudad'),
(@environmental_id, 'Jardines Botánicos', 'Visitar jardines y áreas naturales'),
(@environmental_id, 'Escapadas de Fin de Semana', 'Viajes cortos a lugares cercanos');

-- ==============================================
-- 7. BIENESTAR ESPIRITUAL ✨
-- ==============================================
INSERT INTO pref_categories (name, description) VALUES
('Bienestar Espiritual', 'Prácticas de bienestar y actividades significativas');

SET @spiritual_id = LAST_INSERT_ID();

INSERT INTO preferences (category_id, name, description) VALUES
-- Prácticas de mindfulness
(@spiritual_id, 'Practicar Meditación', 'Sesiones de meditación y mindfulness'),
(@spiritual_id, 'Practicar Yoga', 'Yoga para conexión espiritual'),
(@spiritual_id, 'Oración o Reflexión', 'Prácticas espirituales personales'),
(@spiritual_id, 'Escribir Diario', 'Journaling y auto-reflexión'),
(@spiritual_id, 'Tiempo en Naturaleza', 'Conectar con el entorno natural'),
(@spiritual_id, 'Arte y Creatividad', 'Expresión artística consciente'),
-- Actividades significativas
(@spiritual_id, 'Hacer Voluntariado', 'Ayudar en causas sociales'),
(@spiritual_id, 'Crear Algo Nuevo', 'Proyectos creativos y construcción'),
(@spiritual_id, 'Ayudar a Otros', 'Actos de servicio y apoyo'),
(@spiritual_id, 'Aprender Cosas Nuevas', 'Cursos, talleres y aprendizaje continuo'),
(@spiritual_id, 'Conectar con Personas', 'Conversaciones profundas y conexiones'),
(@spiritual_id, 'Explorar la Ciudad', 'Descubrir nuevos lugares y experiencias');

-- Verificar la inserción
SELECT 
    pc.name as categoria,
    COUNT(p.id) as total_preferencias
FROM pref_categories pc
LEFT JOIN preferences p ON pc.id = p.category_id
GROUP BY pc.id, pc.name
ORDER BY pc.id;

SELECT 'Total de preferencias:' as resultado, COUNT(*) as total FROM preferences;
