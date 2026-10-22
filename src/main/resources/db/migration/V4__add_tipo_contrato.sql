-- ==============================================================================
-- MIGRACIÓN V4: SEPARAR TIPO DE CONTRATO Y CATEGORÍA
-- ==============================================================================

-- 1. Tabla catálogo para tipos de contrato
CREATE TABLE IF NOT EXISTS public.tipo_contrato (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO public.tipo_contrato (nombre) VALUES 
    ('Nombrado'),
    ('Ocasional'),
    ('Cátedra')
ON CONFLICT (nombre) DO NOTHING;

-- 2. Columna tipo_contrato en coordinadores
ALTER TABLE public.coordinadores 
    ADD COLUMN IF NOT EXISTS tipo_contrato VARCHAR(50) DEFAULT 'Nombrado';

-- 3. Si algún registro anterior tenía Ocasional o Cátedra en categoria, moverlo a tipo_contrato
UPDATE public.coordinadores 
SET tipo_contrato = 'Ocasional', categoria = 'Auxiliar' 
WHERE categoria ILIKE 'Ocasional%';

UPDATE public.coordinadores 
SET tipo_contrato = 'Cátedra', categoria = 'Auxiliar' 
WHERE categoria ILIKE 'Cátedra%' OR categoria ILIKE 'Catedra%';
