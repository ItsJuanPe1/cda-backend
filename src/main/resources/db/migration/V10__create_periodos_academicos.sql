CREATE TABLE IF NOT EXISTS public.periodos_academicos (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(20) NOT NULL UNIQUE,
    numero_resolucion VARCHAR(50) NOT NULL UNIQUE,
    fecha_resolucion DATE NOT NULL,
    fecha_inicio_semestre DATE NOT NULL,
    fecha_fin_semestre DATE NOT NULL,
    fecha_adicion_inicio DATE NOT NULL,
    fecha_adicion_fin DATE NOT NULL,
    fecha_cancelacion_inicio DATE NOT NULL,
    fecha_cancelacion_fin DATE NOT NULL,
    plazo_docente_inicio TIMESTAMPTZ NOT NULL,
    plazo_docente_fin TIMESTAMPTZ NOT NULL,
    plazo_coordinador_inicio TIMESTAMPTZ,
    plazo_coordinador_fin TIMESTAMPTZ,
    enlace_resolucion TEXT,
    vigente BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_periodo_semestre CHECK (fecha_fin_semestre > fecha_inicio_semestre),
    CONSTRAINT chk_periodo_adicion CHECK (fecha_adicion_fin >= fecha_adicion_inicio),
    CONSTRAINT chk_periodo_cancelacion CHECK (fecha_cancelacion_fin >= fecha_cancelacion_inicio),
    CONSTRAINT chk_periodo_plazo_docente CHECK (plazo_docente_fin > plazo_docente_inicio),
    CONSTRAINT chk_periodo_plazo_coordinador CHECK (
        (plazo_coordinador_inicio IS NULL AND plazo_coordinador_fin IS NULL)
        OR plazo_coordinador_fin > plazo_coordinador_inicio
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_periodo_vigente
    ON public.periodos_academicos (vigente)
    WHERE vigente = TRUE;
