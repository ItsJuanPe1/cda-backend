-- Completa el versionado de pensum en bases donde V16 quedó registrada
-- pero sus columnas no fueron creadas o quedaron parcialmente aplicadas.
ALTER TABLE public.pensum_academico
    ADD COLUMN IF NOT EXISTS version INT,
    ADD COLUMN IF NOT EXISTS vigente BOOLEAN,
    ADD COLUMN IF NOT EXISTS fecha_desactivacion TIMESTAMP,
    ADD COLUMN IF NOT EXISTS fecha_reactivacion TIMESTAMP;

UPDATE public.pensum_academico
SET version = 1
WHERE version IS NULL;

UPDATE public.pensum_academico
SET vigente = true
WHERE vigente IS NULL;

ALTER TABLE public.pensum_academico
    ALTER COLUMN version SET DEFAULT 1,
    ALTER COLUMN version SET NOT NULL,
    ALTER COLUMN vigente SET DEFAULT true,
    ALTER COLUMN vigente SET NOT NULL;

-- Algunas bases antiguas no tenían clave primaria y acumularon duplicados.
-- Se conserva la primera fila física de cada asignación/version.
DELETE FROM public.pensum_academico duplicate_row
USING public.pensum_academico kept_row
WHERE duplicate_row.ctid < kept_row.ctid
  AND duplicate_row.code_program = kept_row.code_program
  AND duplicate_row.code_course = kept_row.code_course
  AND duplicate_row.version = kept_row.version;

ALTER TABLE public.pensum_academico
    DROP CONSTRAINT IF EXISTS pensum_academico_pkey;

ALTER TABLE public.pensum_academico
    ADD CONSTRAINT pensum_academico_pkey
    PRIMARY KEY (code_program, code_course, version);

CREATE INDEX IF NOT EXISTS idx_pensum_programa_vigente
    ON public.pensum_academico(code_program, vigente);
