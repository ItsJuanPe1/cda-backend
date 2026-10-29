-- ==============================================================================
-- MIGRACIÓN V7: SOPORTE DE DESHABILITACIÓN (SOFT DELETE) PARA ASIGNATURAS Y PENSUM
-- ==============================================================================

-- 1. Agregar columna activo a asignaturas
ALTER TABLE public.asignaturas 
    ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT true;

-- 2. Agregar columna activo a pensum_academico
ALTER TABLE public.pensum_academico 
    ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT true;
