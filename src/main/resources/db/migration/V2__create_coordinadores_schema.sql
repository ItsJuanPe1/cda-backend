-- ==============================================================================
-- MIGRACIÓN V2: CREACIÓN DE TABLA DE COORDINADORES
-- ==============================================================================

CREATE TABLE IF NOT EXISTS public.coordinadores (
    id SERIAL PRIMARY KEY,
    identificacion VARCHAR(50) NOT NULL UNIQUE,
    nombre_completo VARCHAR(150) NOT NULL,
    correo VARCHAR(150),
    correo_institucional VARCHAR(150) NOT NULL UNIQUE,
    telefono VARCHAR(50),
    code_program VARCHAR(50),
    activo BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_coordinador_programa FOREIGN KEY (code_program)
        REFERENCES public.programas(code_program) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_coordinador_identificacion ON public.coordinadores(identificacion);
CREATE INDEX IF NOT EXISTS idx_coordinador_institucional ON public.coordinadores(correo_institucional);
CREATE INDEX IF NOT EXISTS idx_coordinador_programa ON public.coordinadores(code_program);
