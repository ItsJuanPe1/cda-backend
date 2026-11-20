-- Las complementarias que propone el coordinador no ocupan un espacio de la malla: se marcan con
-- code_course_espacio = 'COMPLEMENTARIA' y pueden ser varias por programa y período. El índice único
-- (un curso por espacio, período y programa) solo aplica a los espacios reales de la malla.
DROP INDEX IF EXISTS public.ux_cursos_programacion_espacio;

CREATE UNIQUE INDEX IF NOT EXISTS ux_cursos_programacion_espacio
    ON public.cursos_programacion (periodo_id, code_program, code_course_espacio)
    WHERE code_course_espacio IS NOT NULL
      AND code_course_espacio <> 'COMPLEMENTARIA'
      AND estado <> 'EXCLUIDO';
