-- ==============================================================================
-- V34: HU-059 - Cupos de estudiantes por curso/grupo programado
-- ==============================================================================

ALTER TABLE public.cursos_programacion
    ADD COLUMN IF NOT EXISTS cupos INTEGER;

ALTER TABLE public.cursos_programacion
    DROP CONSTRAINT IF EXISTS ck_curso_cupos_positivos;

ALTER TABLE public.cursos_programacion
    ADD CONSTRAINT ck_curso_cupos_positivos
    CHECK (cupos IS NULL OR cupos > 0);

COMMENT ON COLUMN public.cursos_programacion.cupos
    IS 'HU-059: cupos de estudiantes habilitados para el curso/grupo en el período';
