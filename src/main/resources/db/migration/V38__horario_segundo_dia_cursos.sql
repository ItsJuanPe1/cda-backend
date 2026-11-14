-- Soporte para dividir el horario de un curso en dos días (sesión 1 y sesión 2)
ALTER TABLE public.cursos_programacion
    ADD COLUMN IF NOT EXISTS dia2 SMALLINT,
    ADD COLUMN IF NOT EXISTS hora_inicio2 TIME,
    ADD COLUMN IF NOT EXISTS hora_fin2 TIME,
    ALTER COLUMN horario TYPE VARCHAR(150);

COMMENT ON COLUMN public.cursos_programacion.dia2 IS 'Segundo día de clase cuando se divide el horario en 2 días (1=Lunes, ..., 7=Domingo)';
COMMENT ON COLUMN public.cursos_programacion.hora_inicio2 IS 'Hora de inicio de la segunda sesión de clase';
COMMENT ON COLUMN public.cursos_programacion.hora_fin2 IS 'Hora de finalización de la segunda sesión de clase';
