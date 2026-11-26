-- =============================================================================
-- V5: Disponibilidad horaria docente y accesos extraordinarios
-- Tablas: disponibilidades_docentes, accesos_extraordinarios_docente
-- =============================================================================

-- 1. Franjas de disponibilidad declaradas por cada docente por período
CREATE TABLE IF NOT EXISTS public.disponibilidades_docentes (
    id          BIGSERIAL PRIMARY KEY,
    id_docente  BIGINT    NOT NULL,
    periodo_id  BIGINT    NOT NULL,
    dia         INTEGER   NOT NULL,
    hora_inicio TIME      NOT NULL,
    hora_fin    TIME      NOT NULL,
    CONSTRAINT fk_disponibilidad_docente FOREIGN KEY (id_docente) REFERENCES public.docentes(id) ON DELETE CASCADE,
    CONSTRAINT fk_disponibilidad_periodo FOREIGN KEY (periodo_id) REFERENCES public.periodos_academicos(id) ON DELETE RESTRICT,
    CONSTRAINT ck_disponibilidad_dia     CHECK (dia BETWEEN 1 AND 6),
    CONSTRAINT ck_disponibilidad_horas   CHECK (hora_fin > hora_inicio)
);

CREATE INDEX IF NOT EXISTS ix_disponibilidades_docente_periodo
    ON public.disponibilidades_docentes (id_docente, periodo_id, dia, hora_inicio);

CREATE INDEX IF NOT EXISTS ix_disponibilidades_periodo
    ON public.disponibilidades_docentes (periodo_id);

-- 2. Extensiones temporales de ventana de disponibilidad otorgadas por administración
CREATE TABLE IF NOT EXISTS public.accesos_extraordinarios_docente (
    id           BIGSERIAL    PRIMARY KEY,
    id_docente   BIGINT       NOT NULL REFERENCES public.docentes(id) ON DELETE RESTRICT,
    fecha_inicio TIMESTAMPTZ  NOT NULL,
    fecha_fin    TIMESTAMPTZ  NOT NULL,
    motivo       VARCHAR(500) NOT NULL,
    otorgado_por VARCHAR(150) NOT NULL,
    revocado_en  TIMESTAMPTZ,
    revocado_por VARCHAR(150),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_acceso_extra_fechas     CHECK (fecha_fin > fecha_inicio),
    CONSTRAINT ck_acceso_extra_motivo     CHECK (length(btrim(motivo)) > 0),
    CONSTRAINT ck_acceso_extra_revocacion CHECK ((revocado_en IS NULL) = (revocado_por IS NULL))
);

CREATE INDEX IF NOT EXISTS ix_acceso_extra_docente
    ON public.accesos_extraordinarios_docente (id_docente, fecha_fin DESC);
