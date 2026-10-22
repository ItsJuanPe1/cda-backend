-- ==============================================================================
-- MIGRACIÓN V3: AGREGAR CAMPO CATEGORÍA A COORDINADORES
-- ==============================================================================

ALTER TABLE public.coordinadores 
    ADD COLUMN IF NOT EXISTS categoria VARCHAR(50) DEFAULT 'Auxiliar';
