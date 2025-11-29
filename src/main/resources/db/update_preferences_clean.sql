-- Script para limpiar y actualizar nombres de preferencias
-- Quita referencias específicas de lugares y limpia descripciones

USE nexus_db;

-- ==============================================
-- 1. BIENESTAR FÍSICO - Simplificar nombres
-- ==============================================
UPDATE preferences SET 
    name = 'Gimnasio',
    description = 'Entrenamiento con pesas, máquinas y ejercicio funcional'
WHERE name = 'gym';

UPDATE preferences SET 
    name = 'Yoga',
    description = 'Práctica de yoga, pilates y estiramientos'
WHERE name = 'yoga';

UPDATE preferences SET 
    name = 'Correr',
    description = 'Running y jogging'
WHERE name = 'running_cdmx';

UPDATE preferences SET 
    name = 'Ciclismo',
    description = 'Andar en bicicleta y ciclovías'
WHERE name = 'cycling_cdmx';

UPDATE preferences SET 
    name = 'Natación',
    description = 'Nadar en alberca'
WHERE name = 'swimming';

UPDATE preferences SET 
    name = 'Baile',
    description = 'Salsa, bachata, contemporáneo y otros estilos'
WHERE name = 'dance';

UPDATE preferences SET 
    name = 'Fútbol',
    description = 'Jugar fútbol'
WHERE name = 'soccer';

UPDATE preferences SET 
    name = 'Basquetbol',
    description = 'Jugar básquet'
WHERE name = 'basketball';

UPDATE preferences SET 
    name = 'Voleibol',
    description = 'Jugar voleibol'
WHERE name = 'volleyball';

UPDATE preferences SET 
    name = 'Artes Marciales',
    description = 'Karate, judo, taekwondo, etc.'
WHERE name = 'martial_arts';

UPDATE preferences SET 
    name = 'Escalada',
    description = 'Escalada en roca (indoor/outdoor)'
WHERE name = 'climbing';

UPDATE preferences SET 
    name = 'Senderismo',
    description = 'Caminatas y excursiones en la naturaleza'
WHERE name = 'hiking_near_cdmx';

-- ==============================================
-- 2. BIENESTAR EMOCIONAL - OK, solo simplificar
-- ==============================================
UPDATE preferences SET 
    name = 'Palabras de Afirmación',
    description = 'Expresar amor con palabras y cumplidos'
WHERE name = 'love_words';

UPDATE preferences SET 
    name = 'Tiempo de Calidad',
    description = 'Pasar tiempo juntos sin distracciones'
WHERE name = 'love_quality_time';

UPDATE preferences SET 
    name = 'Regalos',
    description = 'Dar y recibir detalles significativos'
WHERE name = 'love_gifts';

UPDATE preferences SET 
    name = 'Actos de Servicio',
    description = 'Demostrar amor haciendo cosas por el otro'
WHERE name = 'love_acts_service';

UPDATE preferences SET 
    name = 'Contacto Físico',
    description = 'Expresar amor con abrazos, besos y caricias'
WHERE name = 'love_physical_touch';

UPDATE preferences SET 
    name = 'Hablar del Tema',
    description = 'Conversar sobre lo que causa estrés'
WHERE name = 'stress_talk';

UPDATE preferences SET 
    name = 'Tiempo a Solas',
    description = 'Tener espacio personal para procesar'
WHERE name = 'stress_alone_time';

UPDATE preferences SET 
    name = 'Escuchar Música',
    description = 'Relajarse con música'
WHERE name = 'stress_music';

UPDATE preferences SET 
    name = 'Hacer Ejercicio',
    description = 'Liberar estrés con actividad física'
WHERE name = 'stress_exercise';

UPDATE preferences SET 
    name = 'Meditación',
    description = 'Mindfulness y técnicas de relajación'
WHERE name = 'stress_meditation';

UPDATE preferences SET 
    name = 'Hacer Hobbies',
    description = 'Dedicar tiempo a pasatiempos favoritos'
WHERE name = 'stress_hobbies';

-- ==============================================
-- 3. BIENESTAR SOCIAL - Simplificar lugares
-- ==============================================
UPDATE preferences SET 
    name = 'Ir al Cine',
    description = 'Ver películas en sala'
WHERE name = 'date_movies';

UPDATE preferences SET 
    name = 'Cena Romántica',
    description = 'Salir a cenar a restaurantes'
WHERE name = 'date_dinner_cdmx';

UPDATE preferences SET 
    name = 'Conciertos',
    description = 'Asistir a conciertos y shows en vivo'
WHERE name = 'date_concerts';

UPDATE preferences SET 
    name = 'Visitar Museos',
    description = 'Explorar museos y galerías de arte'
WHERE name = 'date_museums_cdmx';

UPDATE preferences SET 
    name = 'Paseos en Parques',
    description = 'Caminar y pasar tiempo en parques'
WHERE name = 'date_parks_cdmx';

UPDATE preferences SET 
    name = 'Quedarse en Casa',
    description = 'Ver películas o cocinar juntos en casa'
WHERE name = 'date_home';

UPDATE preferences SET 
    name = 'Parques de Diversiones',
    description = 'Ir a parques temáticos y de aventura'
WHERE name = 'date_adventure_cdmx';

UPDATE preferences SET 
    name = 'Juegos',
    description = 'Juegos de mesa o videojuegos juntos'
WHERE name = 'date_games';

UPDATE preferences SET 
    name = 'Restaurantes',
    description = 'Explorar diferentes tipos de cocina'
WHERE name = 'venue_restaurants';

UPDATE preferences SET 
    name = 'Cafeterías',
    description = 'Tomar café y conversar'
WHERE name = 'venue_cafes_cdmx';

UPDATE preferences SET 
    name = 'Bares y Antros',
    description = 'Salir a bailar y vida nocturna'
WHERE name = 'venue_bars_cdmx';

UPDATE preferences SET 
    name = 'Teatro',
    description = 'Ver obras de teatro y musicales'
WHERE name = 'venue_theaters_cdmx';

UPDATE preferences SET 
    name = 'Centros Comerciales',
    description = 'Ir de compras juntos'
WHERE name = 'venue_malls_cdmx';

-- ==============================================
-- 4. BIENESTAR INTELECTUAL - OK
-- ==============================================
UPDATE preferences SET 
    name = 'Tecnología',
    description = 'Interés en tecnología e innovación'
WHERE name = 'interest_technology';

UPDATE preferences SET 
    name = 'Ciencia',
    description = 'Curiosidad por temas científicos'
WHERE name = 'interest_science';

UPDATE preferences SET 
    name = 'Arte y Diseño',
    description = 'Apreciación del arte visual'
WHERE name = 'interest_art';

UPDATE preferences SET 
    name = 'Historia',
    description = 'Interés en historia y cultura'
WHERE name = 'interest_history';

UPDATE preferences SET 
    name = 'Política y Actualidad',
    description = 'Seguir noticias y eventos actuales'
WHERE name = 'interest_politics';

UPDATE preferences SET 
    name = 'Música',
    description = 'Descubrir y disfrutar diferentes géneros'
WHERE name = 'interest_music';

UPDATE preferences SET 
    name = 'Literatura',
    description = 'Leer y discutir libros'
WHERE name = 'interest_literature';

UPDATE preferences SET 
    name = 'Filosofía',
    description = 'Reflexionar sobre temas existenciales'
WHERE name = 'interest_philosophy';

UPDATE preferences SET 
    name = 'Leer',
    description = 'Lectura de libros, artículos y más'
WHERE name = 'hobby_reading';

UPDATE preferences SET 
    name = 'Escribir',
    description = 'Escritura creativa o personal'
WHERE name = 'hobby_writing';

UPDATE preferences SET 
    name = 'Pintar o Dibujar',
    description = 'Artes visuales y dibujo'
WHERE name = 'hobby_painting';

UPDATE preferences SET 
    name = 'Tocar Instrumento',
    description = 'Tocar guitarra, piano u otro instrumento'
WHERE name = 'hobby_music_play';

UPDATE preferences SET 
    name = 'Fotografía',
    description = 'Tomar fotos y editar imágenes'
WHERE name = 'hobby_photography';

UPDATE preferences SET 
    name = 'Cocinar',
    description = 'Preparar recetas nuevas y experimentar'
WHERE name = 'hobby_cooking';

UPDATE preferences SET 
    name = 'Videojuegos',
    description = 'Jugar videojuegos solo o en línea'
WHERE name = 'hobby_gaming';

UPDATE preferences SET 
    name = 'Manualidades',
    description = 'Proyectos DIY y trabajos manuales'
WHERE name = 'hobby_crafts';

-- ==============================================
-- 5. BIENESTAR PROFESIONAL - Limpiar valores abstractos
-- ==============================================
UPDATE preferences SET 
    name = 'Dar Palabras de Ánimo',
    description = 'Motivar y alentar con palabras'
WHERE name = 'support_encouragement';

UPDATE preferences SET 
    name = 'Dar Consejos',
    description = 'Ofrecer soluciones y perspectivas'
WHERE name = 'support_advice';

UPDATE preferences SET 
    name = 'Ayuda Práctica',
    description = 'Ayudar activamente en tareas'
WHERE name = 'support_active_help';

UPDATE preferences SET 
    name = 'Dar Espacio',
    description = 'Respetar tiempo personal'
WHERE name = 'support_space';

UPDATE preferences SET 
    name = 'Celebrar Logros',
    description = 'Reconocer y festejar éxitos'
WHERE name = 'support_celebration';

-- Eliminar valores abstractos que no son actividades
DELETE FROM user_preferences WHERE preference_id IN (
    SELECT id FROM preferences WHERE name IN (
        'work_value_growth', 'work_value_stability', 'work_value_creativity',
        'work_value_impact', 'work_value_income', 'work_value_flexibility', 'work_value_passion'
    )
);

DELETE FROM preferences WHERE name IN (
    'work_value_growth', 'work_value_stability', 'work_value_creativity',
    'work_value_impact', 'work_value_income', 'work_value_flexibility', 'work_value_passion'
);

-- ==============================================
-- 6. BIENESTAR AMBIENTAL - Simplificar
-- ==============================================
UPDATE preferences SET 
    name = 'Senderismo en Naturaleza',
    description = 'Caminatas en montañas y bosques'
WHERE name = 'outdoor_hiking_cdmx';

UPDATE preferences SET 
    name = 'Paseos en Parques',
    description = 'Disfrutar áreas verdes urbanas'
WHERE name = 'outdoor_parks_cdmx';

UPDATE preferences SET 
    name = 'Picnics',
    description = 'Comer al aire libre'
WHERE name = 'outdoor_picnics';

UPDATE preferences SET 
    name = 'Paseos en Trajinera',
    description = 'Navegar en canales y lagos'
WHERE name = 'outdoor_xochimilco';

UPDATE preferences SET 
    name = 'Ciclismo al Aire Libre',
    description = 'Rutas en bicicleta por la ciudad'
WHERE name = 'outdoor_cycling';

UPDATE preferences SET 
    name = 'Jardines Botánicos',
    description = 'Visitar jardines y áreas naturales'
WHERE name = 'outdoor_botanical';

UPDATE preferences SET 
    name = 'Escapadas de Fin de Semana',
    description = 'Viajes cortos a lugares cercanos'
WHERE name = 'outdoor_daytrips';

-- Eliminar preferencias abstractas ambientales
DELETE FROM user_preferences WHERE preference_id IN (
    SELECT id FROM preferences WHERE name IN ('env_city_life', 'env_nature_balance', 'env_eco_conscious')
);

DELETE FROM preferences WHERE name IN ('env_city_life', 'env_nature_balance', 'env_eco_conscious');

-- ==============================================
-- 7. BIENESTAR ESPIRITUAL - Mantener solo actividades
-- ==============================================

-- Eliminar valores abstractos
DELETE FROM user_preferences WHERE preference_id IN (
    SELECT id FROM preferences WHERE name IN (
        'value_honesty', 'value_loyalty', 'value_growth', 'value_adventure',
        'value_family', 'value_freedom', 'value_kindness', 'value_ambition'
    )
);

DELETE FROM preferences WHERE name IN (
    'value_honesty', 'value_loyalty', 'value_growth', 'value_adventure',
    'value_family', 'value_freedom', 'value_kindness', 'value_ambition'
);

-- Actualizar prácticas de mindfulness
UPDATE preferences SET 
    name = 'Practicar Meditación',
    description = 'Sesiones de meditación y mindfulness'
WHERE name = 'mindfulness_meditation';

UPDATE preferences SET 
    name = 'Practicar Yoga',
    description = 'Yoga para conexión espiritual'
WHERE name = 'mindfulness_yoga';

UPDATE preferences SET 
    name = 'Oración o Reflexión',
    description = 'Prácticas espirituales personales'
WHERE name = 'mindfulness_prayer';

UPDATE preferences SET 
    name = 'Escribir Diario',
    description = 'Journaling y auto-reflexión'
WHERE name = 'mindfulness_journaling';

UPDATE preferences SET 
    name = 'Tiempo en Naturaleza',
    description = 'Conectar con el entorno natural'
WHERE name = 'mindfulness_nature';

UPDATE preferences SET 
    name = 'Arte y Creatividad',
    description = 'Expresión artística consciente'
WHERE name = 'mindfulness_art';

-- Actualizar actividades significativas
UPDATE preferences SET 
    name = 'Hacer Voluntariado',
    description = 'Ayudar en causas sociales'
WHERE name = 'meaningful_volunteering';

UPDATE preferences SET 
    name = 'Crear Algo Nuevo',
    description = 'Proyectos creativos y construcción'
WHERE name = 'meaningful_creating';

UPDATE preferences SET 
    name = 'Ayudar a Otros',
    description = 'Actos de servicio y apoyo'
WHERE name = 'meaningful_helping';

UPDATE preferences SET 
    name = 'Aprender Cosas Nuevas',
    description = 'Cursos, talleres y aprendizaje continuo'
WHERE name = 'meaningful_learning';

UPDATE preferences SET 
    name = 'Conectar con Personas',
    description = 'Conversaciones profundas y conexiones'
WHERE name = 'meaningful_connecting';

UPDATE preferences SET 
    name = 'Explorar la Ciudad',
    description = 'Descubrir nuevos lugares y experiencias'
WHERE name = 'meaningful_exploring';

-- Verificar resultado final
SELECT 
    pc.name as categoria,
    COUNT(p.id) as total_preferencias
FROM pref_categories pc
LEFT JOIN preferences p ON pc.id = p.category_id
GROUP BY pc.id, pc.name
ORDER BY pc.id;
