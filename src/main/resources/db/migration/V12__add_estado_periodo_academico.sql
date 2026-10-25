ALTER TABLE public.periodos_academicos
    ADD COLUMN IF NOT EXISTS estado VARCHAR(20);

UPDATE public.periodos_academicos
SET estado = CASE WHEN vigente THEN 'VIGENTE' ELSE 'HISTORIAL' END
WHERE estado IS NULL;

ALTER TABLE public.periodos_academicos
    ALTER COLUMN estado SET DEFAULT 'VIGENTE',
    ALTER COLUMN estado SET NOT NULL;

ALTER TABLE public.periodos_academicos
    ADD CONSTRAINT chk_periodo_estado
    CHECK (estado IN ('VIGENTE', 'HISTORIAL', 'DESACTIVADO'));
