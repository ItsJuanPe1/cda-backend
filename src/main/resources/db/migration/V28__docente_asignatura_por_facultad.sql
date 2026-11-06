-- ==============================================================================
-- V28: HISTÓRICO DOCENTE-ASIGNATURA POR FACULTAD (HU-049)
-- ==============================================================================
-- El administrador ya no elige el programa: la asociación queda ligada a la
-- facultad de adscripción del docente. El programa se conserva en el curso de
-- programación, pero el histórico se consulta por facultad.
-- Tampoco existe la carga inicial: las filas CARGA_INICIAL pasan a MANUAL.

ALTER TABLE public.docente_asignatura
    ADD COLUMN id_facultad INTEGER;

-- Backfill desde el programa de la asociación y, si no hay, desde la asignatura.
UPDATE public.docente_asignatura da
SET id_facultad = p.id_facultad_programa
FROM public.programas p
WHERE p.code_program = da.code_program
  AND da.id_facultad IS NULL
  AND p.id_facultad_programa IS NOT NULL;

UPDATE public.docente_asignatura da
SET id_facultad = a.id_facultad
FROM public.asignaturas a
WHERE a.code_course = da.code_course
  AND da.id_facultad IS NULL
  AND a.id_facultad IS NOT NULL;

-- Solo pueden quedar filas sin facultad si ni el programa ni la asignatura la
-- tienen. Esas filas no son representables en el nuevo modelo.
DELETE FROM public.docente_asignatura WHERE id_facultad IS NULL;

-- Varios programas de la misma facultad pueden ofrecer la misma asignatura. En
-- el nuevo modelo eso es una sola asociación: se conserva la fila activa más
-- reciente y se descartan las demás variantes.
WITH duplicadas AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY docente_id, code_course, id_facultad
               ORDER BY activo DESC, updated_at DESC, id DESC
           ) AS posicion
    FROM public.docente_asignatura
)
DELETE FROM public.docente_asignatura da
USING duplicadas d
WHERE da.id = d.id
  AND d.posicion > 1;

ALTER TABLE public.docente_asignatura
    DROP CONSTRAINT fk_docente_asignatura_programa;

ALTER TABLE public.docente_asignatura
    DROP CONSTRAINT uq_docente_asignatura_programa;

ALTER TABLE public.docente_asignatura
    DROP COLUMN code_program;

ALTER TABLE public.docente_asignatura
    ALTER COLUMN id_facultad SET NOT NULL;

ALTER TABLE public.docente_asignatura
    ADD CONSTRAINT fk_docente_asignatura_facultad FOREIGN KEY (id_facultad)
        REFERENCES public."Facultad"(id) ON DELETE RESTRICT;

ALTER TABLE public.docente_asignatura
    ADD CONSTRAINT uq_docente_asignatura_facultad
        UNIQUE (docente_id, code_course, id_facultad);

ALTER TABLE public.docente_asignatura
    DROP CONSTRAINT chk_docente_asignatura_origen;

UPDATE public.docente_asignatura
SET origen = 'MANUAL'
WHERE origen = 'CARGA_INICIAL';

ALTER TABLE public.docente_asignatura
    ADD CONSTRAINT chk_docente_asignatura_origen
        CHECK (origen IN ('MANUAL', 'PROGRAMACION'));

CREATE INDEX idx_docente_asignatura_facultad
    ON public.docente_asignatura(id_facultad);

-- Criterio 4: al asignar un docente a un curso se actualiza su histórico. La
-- facultad se deriva del programa del curso y, si falta, de la asignatura.
CREATE OR REPLACE FUNCTION public.fn_curso_docente_historico()
RETURNS TRIGGER AS $$
DECLARE
    v_facultad INTEGER;
BEGIN
    IF NEW.id_docente IS NOT NULL THEN
        SELECT COALESCE(p.id_facultad_programa, a.id_facultad)
        INTO v_facultad
        FROM public.programas p
        JOIN public.asignaturas a ON a.code_course = NEW.code_course
        WHERE p.code_program = NEW.code_program;

        IF v_facultad IS NOT NULL THEN
            INSERT INTO public.docente_asignatura (
                docente_id, code_course, id_facultad, periodo_id, origen, activo, updated_at
            ) VALUES (
                NEW.id_docente, NEW.code_course, v_facultad, NEW.periodo_id,
                'PROGRAMACION', TRUE, CURRENT_TIMESTAMP
            )
            ON CONFLICT (docente_id, code_course, id_facultad) DO UPDATE
            SET activo = TRUE,
                desactivado_at = NULL,
                periodo_id = EXCLUDED.periodo_id,
                updated_at = CURRENT_TIMESTAMP;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
