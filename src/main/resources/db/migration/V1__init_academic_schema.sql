-- ==============================================================================
-- SISTEMA DE PROGRAMACIÓN ACADÉMICA - MIGRACIÓN INICIAL FLYWAY
-- ==============================================================================

-- 1. Tabla de Facultades (debe crearse primero para que las demás puedan referenciarla)
CREATE TABLE IF NOT EXISTS public."Facultad" (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    descripcion TEXT
);

-- 2. Tabla de Asignaturas
CREATE TABLE IF NOT EXISTS public.asignaturas (
    code_course VARCHAR(50) PRIMARY KEY,
    name_course VARCHAR(150) NOT NULL,
    credits INT DEFAULT 0,
    is_elective BOOLEAN DEFAULT false,
    es_complementaria BOOLEAN DEFAULT false,
    es_basica BOOLEAN DEFAULT false,
    facultad VARCHAR(150),
    id_facultad INT,
    CONSTRAINT fk_asignaturas_facultad FOREIGN KEY (id_facultad) 
        REFERENCES public."Facultad"(id) ON DELETE SET NULL
);

-- 3. Tabla de Programas Académicos (Carreras)
CREATE TABLE IF NOT EXISTS public.programas (
    code_program VARCHAR(50) PRIMARY KEY,
    name_program VARCHAR(150) NOT NULL,
    numero_semestre INT NOT NULL DEFAULT 10,
    id_facultad_programa INT,
    CONSTRAINT fk_programas_facultad FOREIGN KEY (id_facultad_programa) 
        REFERENCES public."Facultad"(id) ON DELETE SET NULL
);

-- 4. Tabla Intermedia: Pensum Académico (Malla Curricular)
CREATE TABLE IF NOT EXISTS public.pensum_academico (
    code_program VARCHAR(50) NOT NULL,
    code_course VARCHAR(50) NOT NULL,
    semestre INT NOT NULL,
    is_elective BOOLEAN DEFAULT false,
    facultad VARCHAR(150),
    
    PRIMARY KEY (code_program, code_course),
    CONSTRAINT fk_pensum_programa FOREIGN KEY (code_program) 
        REFERENCES public.programas(code_program) ON DELETE CASCADE,
    CONSTRAINT fk_pensum_asignatura FOREIGN KEY (code_course) 
        REFERENCES public.asignaturas(code_course) ON DELETE CASCADE
);

DO $$
BEGIN
    -- Asegurar columnas en asignaturas
    ALTER TABLE public.asignaturas ADD COLUMN IF NOT EXISTS facultad VARCHAR(150);
    ALTER TABLE public.asignaturas ADD COLUMN IF NOT EXISTS id_facultad INT;
    ALTER TABLE public.asignaturas ADD COLUMN IF NOT EXISTS credits INT DEFAULT 0;
    ALTER TABLE public.asignaturas ADD COLUMN IF NOT EXISTS is_elective BOOLEAN DEFAULT false;
    ALTER TABLE public.asignaturas ADD COLUMN IF NOT EXISTS es_complementaria BOOLEAN DEFAULT false;
    ALTER TABLE public.asignaturas ADD COLUMN IF NOT EXISTS es_basica BOOLEAN DEFAULT false;

    -- Asegurar columnas en programas
    ALTER TABLE public.programas ADD COLUMN IF NOT EXISTS numero_semestre INT DEFAULT 10;
    ALTER TABLE public.programas ADD COLUMN IF NOT EXISTS id_facultad_programa INT;

    -- Asegurar columnas en pensum
    ALTER TABLE public.pensum_academico ADD COLUMN IF NOT EXISTS is_elective BOOLEAN DEFAULT false;
    ALTER TABLE public.pensum_academico ADD COLUMN IF NOT EXISTS facultad VARCHAR(150);

    -- FK asignaturas -> Facultad
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_asignaturas_facultad'
    ) THEN
        ALTER TABLE public.asignaturas
            ADD CONSTRAINT fk_asignaturas_facultad
            FOREIGN KEY (id_facultad) REFERENCES public."Facultad"(id) ON DELETE SET NULL;
    END IF;

    -- FK programas -> Facultad
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_programas_facultad'
    ) THEN
        ALTER TABLE public.programas
            ADD CONSTRAINT fk_programas_facultad
            FOREIGN KEY (id_facultad_programa) REFERENCES public."Facultad"(id) ON DELETE SET NULL;
    END IF;

    -- FK pensum -> programas
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pensum_programa'
    ) THEN
        ALTER TABLE public.pensum_academico
            ADD CONSTRAINT fk_pensum_programa
            FOREIGN KEY (code_program) REFERENCES public.programas(code_program) ON DELETE CASCADE;
    END IF;

    -- FK pensum -> asignaturas
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pensum_asignatura'
    ) THEN
        ALTER TABLE public.pensum_academico
            ADD CONSTRAINT fk_pensum_asignatura
            FOREIGN KEY (code_course) REFERENCES public.asignaturas(code_course) ON DELETE CASCADE;
    END IF;
END $$;

-- 6. Índices de optimización para búsquedas y relaciones foráneas
CREATE INDEX IF NOT EXISTS idx_pensum_program ON public.pensum_academico(code_program);
CREATE INDEX IF NOT EXISTS idx_pensum_course ON public.pensum_academico(code_course);
CREATE INDEX IF NOT EXISTS idx_pensum_semestre ON public.pensum_academico(code_program, semestre);
CREATE INDEX IF NOT EXISTS idx_asignaturas_facultad ON public.asignaturas(id_facultad);
CREATE INDEX IF NOT EXISTS idx_programas_facultad ON public.programas(id_facultad_programa);
