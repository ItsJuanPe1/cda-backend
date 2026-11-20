-- Electivas profesionales programadas a mano por el coordinador.
-- code_course_espacio: código del espacio de la malla (ELE-PROF-I, II, ...) que ocupa la electiva.
-- Es NULL en los cursos que salen de la malla obligatoria. Sin FK: el espacio se valida en el servicio.
ALTER TABLE public.cursos_programacion
    ADD COLUMN IF NOT EXISTS code_course_espacio VARCHAR(50);

-- Un espacio de la malla tiene una sola electiva activa por período y programa.
CREATE UNIQUE INDEX IF NOT EXISTS ux_cursos_programacion_espacio
    ON public.cursos_programacion (periodo_id, code_program, code_course_espacio)
    WHERE code_course_espacio IS NOT NULL AND estado <> 'EXCLUIDO';
