-- Corregir valores del enum CalendarSource para que estén en mayúsculas
-- Usando la columna id para evitar el modo safe update
UPDATE calendars 
SET source = 'LOCAL' 
WHERE id > 0 AND LOWER(source) = 'local' AND source != 'LOCAL';

UPDATE calendars 
SET source = 'GOOGLE' 
WHERE id > 0 AND LOWER(source) = 'google' AND source != 'GOOGLE';

UPDATE calendars 
SET source = 'OUTLOOK' 
WHERE id > 0 AND LOWER(source) = 'outlook' AND source != 'OUTLOOK';

-- Verificar los valores
SELECT id, owner_user_id, source, name, device_calendar_id 
FROM calendars 
WHERE deleted_at IS NULL;
