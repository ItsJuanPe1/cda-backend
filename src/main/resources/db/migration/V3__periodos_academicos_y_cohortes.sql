-- =============================================================================
-- V3: Períodos académicos y oferta de cohortes por período
-- Tablas: periodos_academicos, cohortes_periodo
-- =============================================================================

-- 1. Períodos académicos (semestres, vigencia y ventanas de plazos)
CREATE TABLE IF NOT EXISTS public.periodos_academicos (
    id                       BIGSERIAL   PRIMARY KEY,
    nombre                   VARCHAR(20) NOT NULL UNIQUE,
    numero_resolucion        VARCHAR(50) NOT NULL UNIQUE,
    fecha_resolucion         DATE        NOT NULL,
    fecha_inicio_semestre    DATE        NOT NULL,
    fecha_fin_semestre       DATE        NOT NULL,
    fecha_adicion_inicio     DATE        NOT NULL,
    fecha_adicion_fin        DATE        NOT NULL,
    fecha_cancelacion_inicio DATE        NOT NULL,
    fecha_cancelacion_fin    DATE        NOT NULL,
    plazo_docente_inicio     TIMESTAMPTZ NOT NULL,
    plazo_docente_fin        TIMESTAMPTZ NOT NULL,
    plazo_coordinador_inicio TIMESTAMPTZ,
    plazo_coordinador_fin    TIMESTAMPTZ,
    enlace_resolucion        TEXT,
    vigente                  BOOLEAN     NOT NULL DEFAULT FALSE,
    estado                   VARCHAR(20) NOT NULL DEFAULT 'VIGENTE',
    version                  BIGINT      NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_periodo_semestre          CHECK (fecha_fin_semestre > fecha_inicio_semestre),
    CONSTRAINT chk_periodo_adicion           CHECK (fecha_adicion_fin >= fecha_adicion_inicio),
    CONSTRAINT chk_periodo_cancelacion       CHECK (fecha_cancelacion_fin >= fecha_cancelacion_inicio),
    CONSTRAINT chk_periodo_plazo_docente     CHECK (plazo_docente_fin > plazo_docente_inicio),
    CONSTRAINT chk_periodo_plazo_coordinador CHECK (
        (plazo_coordinador_inicio IS NULL AND plazo_coordinador_fin IS NULL)
        OR plazo_coordinador_fin > plazo_coordinador_inicio
    ),
    CONSTRAINT chk_periodo_estado            CHECK (estado IN ('VIGENTE', 'HISTORIAL', 'DESACTIVADO'))
);

-- Solo un período académico puede estar vigente a la vez
CREATE UNIQUE INDEX IF NOT EXISTS ux_periodo_vigente
    ON public.periodos_academicos (vigente)
    WHERE vigente = TRUE;

CREATE INDEX IF NOT EXISTS idx_periodos_inicio_semestre
    ON public.periodos_academicos (fecha_inicio_semestre DESC);

-- 2. Cohortes (semestres ofertados y confirmados por programa en cada período)
CREATE TABLE IF NOT EXISTS public.cohortes_periodo (
    id                BIGSERIAL   PRIMARY KEY,
    periodo_id        BIGINT      NOT NULL REFERENCES public.periodos_academicos(id) ON DELETE RESTRICT,
    code_program      VARCHAR(50) NOT NULL REFERENCES public.programas(code_program) ON DELETE RESTRICT,
    semestre          INTEGER     NOT NULL CHECK (semestre >= 1 AND semestre <= 20),
    jornada           VARCHAR(10) NOT NULL DEFAULT 'DIURNA' CHECK (jornada IN ('DIURNA', 'NOCTURNA')),
    numero_resolucion VARCHAR(50) NOT NULL,
    confirmada        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cohorte_periodo_programa_semestre_jornada
        UNIQUE (periodo_id, code_program, semestre, jornada)
);

CREATE INDEX IF NOT EXISTS idx_cohortes_periodo_programa
    ON public.cohortes_periodo (periodo_id, code_program);
