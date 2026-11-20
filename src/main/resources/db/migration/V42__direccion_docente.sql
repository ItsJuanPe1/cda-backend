-- Dirección de vivienda del docente. Opcional: la edita el propio docente desde «Mi perfil»
-- y el administrador desde la card del docente en Plantilla.
ALTER TABLE public.docentes
    ADD COLUMN IF NOT EXISTS direccion VARCHAR(150);
