-- Repara bases existentes que fueron creadas desde un esquema anterior
-- y tienen registrada V15 pero no todas sus columnas de programas.
ALTER TABLE public.programas
    ADD COLUMN IF NOT EXISTS jornada VARCHAR(10) DEFAULT 'DIURNA',
    ADD COLUMN IF NOT EXISTS semestres_nocturna INT,
    ADD COLUMN IF NOT EXISTS ingreso VARCHAR(10) DEFAULT 'SEMESTRAL',
    ADD COLUMN IF NOT EXISTS estado VARCHAR(10) DEFAULT 'ACTIVO',
    ADD COLUMN IF NOT EXISTS apertura VARCHAR(10);

UPDATE public.programas
SET jornada = 'DIURNA'
WHERE jornada IS NULL;

UPDATE public.programas
SET ingreso = 'SEMESTRAL'
WHERE ingreso IS NULL;

UPDATE public.programas
SET estado = 'ACTIVO'
WHERE estado IS NULL;

ALTER TABLE public.programas
    ALTER COLUMN jornada SET NOT NULL,
    ALTER COLUMN ingreso SET NOT NULL,
    ALTER COLUMN estado SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_programas_jornada'
    ) THEN
        ALTER TABLE public.programas
            ADD CONSTRAINT chk_programas_jornada
            CHECK (jornada IN ('DIURNA', 'NOCTURNA', 'AMBAS'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_programas_ingreso'
    ) THEN
        ALTER TABLE public.programas
            ADD CONSTRAINT chk_programas_ingreso
            CHECK (ingreso IN ('SEMESTRAL', 'ANUAL'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_programas_estado'
    ) THEN
        ALTER TABLE public.programas
            ADD CONSTRAINT chk_programas_estado
            CHECK (estado IN ('ACTIVO', 'INACTIVO'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_programas_apertura'
    ) THEN
        ALTER TABLE public.programas
            ADD CONSTRAINT chk_programas_apertura
            CHECK (apertura IS NULL OR apertura IN ('SEMESTRAL', 'ANUAL'));
    END IF;
END $$;
