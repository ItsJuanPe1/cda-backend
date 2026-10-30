-- ==============================================================================
-- V23: VINCULAR DOCENTES A FACULTAD EN LUGAR DE PROGRAMA
-- ==============================================================================

ALTER TABLE public.docentes
    ADD COLUMN IF NOT EXISTS id_facultad INTEGER REFERENCES public."Facultad"(id) ON DELETE RESTRICT;

ALTER TABLE public.docentes
    ALTER COLUMN code_program DROP NOT NULL;

UPDATE public.docentes d
SET id_facultad = p.id_facultad_programa
FROM public.programas p
WHERE d.code_program = p.code_program
  AND d.id_facultad IS NULL
  AND p.id_facultad_programa IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_docentes_facultad ON public.docentes(id_facultad);

