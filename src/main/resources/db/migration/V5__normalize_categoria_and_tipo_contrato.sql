-- ==============================================================================
-- MIGRACIÓN V5: NORMALIZACIÓN RELACIONAL DE CATEGORÍA Y TIPO DE CONTRATO (3FN)
-- ==============================================================================

-- 1. Tabla de Categorías (Escalafón Docente)
CREATE TABLE IF NOT EXISTS public.categoria (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO public.categoria (nombre) VALUES 
    ('Auxiliar'),
    ('Asistente'),
    ('Asociado'),
    ('Titular')
ON CONFLICT (nombre) DO NOTHING;

-- 2. Tabla de Tipos de Contrato
CREATE TABLE IF NOT EXISTS public.tipo_contrato (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO public.tipo_contrato (nombre) VALUES 
    ('Nombrado'),
    ('Ocasional'),
    ('Cátedra')
ON CONFLICT (nombre) DO NOTHING;

-- 3. Agregar columnas de llaves foráneas a la tabla coordinadores
ALTER TABLE public.coordinadores 
    ADD COLUMN IF NOT EXISTS id_categoria INT,
    ADD COLUMN IF NOT EXISTS id_tipo_contrato INT;

-- 4. Migrar los datos existentes de texto a sus IDs correspondientes
DO $$
BEGIN
    -- Mapeo de categoria a id_categoria si existía la columna de texto
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'coordinadores' AND column_name = 'categoria'
    ) THEN
        UPDATE public.coordinadores c
        SET id_categoria = cat.id
        FROM public.categoria cat
        WHERE c.categoria ILIKE cat.nombre AND c.id_categoria IS NULL;
    END IF;

    -- Asignar Auxiliar por defecto a los que queden sin categoría
    UPDATE public.coordinadores
    SET id_categoria = (SELECT id FROM public.categoria WHERE nombre = 'Auxiliar')
    WHERE id_categoria IS NULL;

    -- Mapeo de tipo_contrato a id_tipo_contrato si existía la columna de texto
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'coordinadores' AND column_name = 'tipo_contrato'
    ) THEN
        UPDATE public.coordinadores c
        SET id_tipo_contrato = tc.id
        FROM public.tipo_contrato tc
        WHERE c.tipo_contrato ILIKE tc.nombre AND c.id_tipo_contrato IS NULL;
    END IF;

    -- Asignar Nombrado por defecto a los que queden sin tipo de contrato
    UPDATE public.coordinadores
    SET id_tipo_contrato = (SELECT id FROM public.tipo_contrato WHERE nombre = 'Nombrado')
    WHERE id_tipo_contrato IS NULL;
END $$;

-- 5. Crear restricciones de Llave Foránea (FK) de forma idempotente
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_coordinador_categoria'
    ) THEN
        ALTER TABLE public.coordinadores
            ADD CONSTRAINT fk_coordinador_categoria
            FOREIGN KEY (id_categoria) REFERENCES public.categoria(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_coordinador_tipo_contrato'
    ) THEN
        ALTER TABLE public.coordinadores
            ADD CONSTRAINT fk_coordinador_tipo_contrato
            FOREIGN KEY (id_tipo_contrato) REFERENCES public.tipo_contrato(id) ON DELETE SET NULL;
    END IF;
END $$;

-- 6. Índices para acelerar búsquedas y joins
CREATE INDEX IF NOT EXISTS idx_coordinador_categoria ON public.coordinadores(id_categoria);
CREATE INDEX IF NOT EXISTS idx_coordinador_tipo_contrato ON public.coordinadores(id_tipo_contrato);
