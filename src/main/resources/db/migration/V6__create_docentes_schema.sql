-- ==============================================================================
-- HU-026: TABLA DE DOCENTES
-- ==============================================================================

CREATE TABLE IF NOT EXISTS public.docentes (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    nombres VARCHAR(30) NOT NULL,
    apellidos VARCHAR(30) NOT NULL,
    tipo_documento VARCHAR(10) NOT NULL,
    numero_documento VARCHAR(11) NOT NULL UNIQUE,
    correo_institucional VARCHAR(50) NOT NULL UNIQUE,
    telefono VARCHAR(20),
    tipo_docente VARCHAR(20) NOT NULL,
    categoria VARCHAR(30) NOT NULL,
    code_program VARCHAR(50) NOT NULL,
    foto_url TEXT,
    evaluacion_periodo VARCHAR(10),
    evaluacion_valor NUMERIC(4,2),
    estado BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_docentes_programa FOREIGN KEY (code_program)
        REFERENCES public.programas(code_program) ON DELETE RESTRICT,

    CONSTRAINT chk_evaluacion_periodo_requerido
        CHECK (evaluacion_valor IS NULL OR evaluacion_periodo IS NOT NULL),

    CONSTRAINT chk_tipo_documento
        CHECK (tipo_documento IN ('CC', 'CE', 'PP')),

    CONSTRAINT chk_tipo_docente
        CHECK (tipo_docente IN ('NOMBRADO', 'HORA_CATEDRA')),

    CONSTRAINT chk_categoria
        CHECK (categoria IN ('AUXILIAR', 'ASISTENTE', 'ASOCIADO', 'TITULAR'))
);

-- Índices de optimización
CREATE INDEX IF NOT EXISTS idx_docentes_programa ON public.docentes(code_program);
CREATE INDEX IF NOT EXISTS idx_docentes_correo ON public.docentes(correo_institucional);
CREATE INDEX IF NOT EXISTS idx_docentes_documento ON public.docentes(numero_documento);
