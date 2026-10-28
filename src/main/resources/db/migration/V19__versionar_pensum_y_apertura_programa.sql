-- Versionado de mallas curriculares sin crear tablas adicionales.
ALTER TABLE public.pensum_academico
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS vigente BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS fecha_desactivacion TIMESTAMP,
    ADD COLUMN IF NOT EXISTS fecha_reactivacion TIMESTAMP;

ALTER TABLE public.pensum_academico
    DROP CONSTRAINT IF EXISTS pensum_academico_pkey;

ALTER TABLE public.pensum_academico
    ADD CONSTRAINT pensum_academico_pkey PRIMARY KEY (code_program, code_course, version);

CREATE INDEX IF NOT EXISTS idx_pensum_programa_vigente
    ON public.pensum_academico(code_program, vigente);

ALTER TABLE public.programas
    ADD COLUMN IF NOT EXISTS apertura VARCHAR(10);

ALTER TABLE public.programas
    DROP CONSTRAINT IF EXISTS chk_programas_apertura;

ALTER TABLE public.programas
    ADD CONSTRAINT chk_programas_apertura
    CHECK (apertura IS NULL OR apertura IN ('SEMESTRAL', 'ANUAL'));
