-- =============================================================================
-- V2: Usuarios, Docentes, Escuelas de Docentes y Coordinadores
-- Tablas: usuarios, docentes, docente_escuelas, coordinadores
-- =============================================================================

-- 1. Cuentas de acceso (el estado activo/inactivo vive en docentes.estado y coordinadores.activo)
CREATE TABLE IF NOT EXISTS public.usuarios (
    id                  SERIAL       PRIMARY KEY,
    nombre              VARCHAR(150) NOT NULL,
    apellido            VARCHAR(150) NOT NULL,
    celular             VARCHAR(10),
    email_institucional TEXT         NOT NULL,
    email_personal      TEXT,
    rol                 INTEGER      NOT NULL,
    detalle_docente     TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_usuarios_email_institucional
    ON public.usuarios (LOWER(email_institucional));

-- 2. Plantilla de docentes (compatible con carga masiva CSV: codigo nullable,
--    OCASIONAL/CATEDRA/HORA_CATEDRA y categorías con o sin prefijo 'PROFESOR ')
CREATE TABLE IF NOT EXISTS public.docentes (
    id                   BIGSERIAL    PRIMARY KEY,
    codigo               VARCHAR(20)  UNIQUE,
    nombres              VARCHAR(100) NOT NULL,
    apellidos            VARCHAR(100) NOT NULL,
    tipo_documento       VARCHAR(10)  NOT NULL,
    numero_documento     VARCHAR(20)  NOT NULL UNIQUE,
    correo_institucional VARCHAR(150) NOT NULL UNIQUE,
    correo_personal      VARCHAR(100),
    telefono             VARCHAR(20),
    celular              VARCHAR(10),
    direccion            VARCHAR(150),
    tipo_docente         VARCHAR(20),
    categoria            VARCHAR(30),
    estado               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_tipo_documento           CHECK (tipo_documento IN ('CC', 'CE', 'PP')),
    CONSTRAINT chk_tipo_docente             CHECK (tipo_docente IS NULL OR tipo_docente IN ('NOMBRADO', 'OCASIONAL', 'CATEDRA', 'HORA_CATEDRA')),
    CONSTRAINT chk_categoria                CHECK (categoria IS NULL OR categoria IN (
        'AUXILIAR', 'ASISTENTE', 'ASOCIADO', 'TITULAR',
        'PROFESOR AUXILIAR', 'PROFESOR ASISTENTE', 'PROFESOR ASOCIADO', 'PROFESOR TITULAR'
    )),
    CONSTRAINT chk_docentes_celular_digitos CHECK (celular IS NULL OR celular ~ '^[0-9]{7,10}$')
);

CREATE INDEX IF NOT EXISTS idx_docentes_estado ON public.docentes (estado);
CREATE INDEX IF NOT EXISTS idx_docentes_correo ON public.docentes (LOWER(correo_institucional));

-- 3. Relación N:M docentes <-> unidades académicas (un docente puede pertenecer a varias y las gestiona desde su perfil)
CREATE TABLE IF NOT EXISTS public.docente_unidades_academicas (
    docente_id              BIGINT     NOT NULL REFERENCES public.docentes(id) ON DELETE CASCADE,
    codigo_unidad_academica VARCHAR(3) NOT NULL REFERENCES public.unidades_academicas(codigo) ON DELETE RESTRICT,
    PRIMARY KEY (docente_id, codigo_unidad_academica)
);

CREATE INDEX IF NOT EXISTS idx_docente_unidades_academicas_unidad
    ON public.docente_unidades_academicas (codigo_unidad_academica);

-- 4. Coordinadores de programas (ligados a un docente + programa + correo institucional de coordinación)
CREATE TABLE IF NOT EXISTS public.coordinadores (
    id                   SERIAL       PRIMARY KEY,
    docente_id           BIGINT       NOT NULL REFERENCES public.docentes(id) ON DELETE RESTRICT,
    code_program         VARCHAR(50)  NOT NULL REFERENCES public.programas(code_program) ON DELETE RESTRICT,
    correo_institucional VARCHAR(150) NOT NULL,
    activo               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- Solo un coordinador activo por programa
CREATE UNIQUE INDEX IF NOT EXISTS uq_coordinadores_programa_activo
    ON public.coordinadores (code_program)
    WHERE activo = TRUE;

CREATE INDEX IF NOT EXISTS idx_coordinadores_correo
    ON public.coordinadores (LOWER(correo_institucional));

CREATE INDEX IF NOT EXISTS idx_coordinadores_docente
    ON public.coordinadores (docente_id);
