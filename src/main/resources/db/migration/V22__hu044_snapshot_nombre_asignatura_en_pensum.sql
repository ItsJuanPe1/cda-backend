-- HU-044: conserva el nombre usado por cada versión de una malla curricular.
-- Se usa VARCHAR(150) para no truncar nombres históricos preexistentes de hasta 150 caracteres.
ALTER TABLE public.pensum_academico
    ADD COLUMN IF NOT EXISTS name_course_snapshot VARCHAR(150);

-- Para los registros existentes solo puede copiarse el nombre actualmente disponible.
-- Si el nombre se cambió antes de esta migración, el nombre histórico anterior ya no
-- está almacenado y no puede reconstruirse automáticamente.
UPDATE public.pensum_academico p
SET name_course_snapshot = a.name_course
    FROM public.asignaturas a
WHERE p.code_course = a.code_course
  AND p.name_course_snapshot IS NULL;

-- Evita que un borrado físico del catálogo elimine silenciosamente una malla.
-- El flujo funcional debe usar asignaturas.activo = false.
ALTER TABLE public.pensum_academico
DROP CONSTRAINT IF EXISTS pensum_academico_code_course_fkey;

ALTER TABLE public.pensum_academico
    ADD CONSTRAINT pensum_academico_code_course_fkey
        FOREIGN KEY (code_course)
            REFERENCES public.asignaturas(code_course)
            ON DELETE RESTRICT;
