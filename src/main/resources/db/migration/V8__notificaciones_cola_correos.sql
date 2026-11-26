-- =============================================================================
-- V8: Cola de notificaciones y envíos de correo (HU-038 / HU-039)
-- Tablas: notificaciones, notificaciones_envios
-- =============================================================================

-- 1. Eventos programados de notificación
CREATE TABLE IF NOT EXISTS public.notificaciones (
    id                 BIGSERIAL    PRIMARY KEY,
    tipo_evento        VARCHAR(50)  NOT NULL,
    periodo_id         BIGINT       REFERENCES public.periodos_academicos(id) ON DELETE RESTRICT,
    fecha_programada   TIMESTAMPTZ  NOT NULL,
    estado             VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    datos              JSONB,
    motivo_cancelacion VARCHAR(100),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    procesada_at       TIMESTAMPTZ,
    cancelada_at       TIMESTAMPTZ,
    CONSTRAINT chk_notificaciones_estado CHECK (estado IN ('PENDIENTE', 'PROCESADO', 'CANCELADO'))
);

CREATE INDEX IF NOT EXISTS ix_notificaciones_pendientes
    ON public.notificaciones (fecha_programada ASC, id ASC)
    WHERE estado = 'PENDIENTE';

CREATE INDEX IF NOT EXISTS ix_notificaciones_periodo_estado
    ON public.notificaciones (periodo_id, estado);

-- 2. Envíos individuales y copias con soporte de reintentos y SKIP LOCKED
CREATE TABLE IF NOT EXISTS public.notificaciones_envios (
    id                   BIGSERIAL    PRIMARY KEY,
    notificacion_id      BIGINT       NOT NULL REFERENCES public.notificaciones(id) ON DELETE CASCADE,
    tipo_destinatario    VARCHAR(20)  NOT NULL,
    destinatario_id      BIGINT,
    estado               VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    intentos             INTEGER      NOT NULL DEFAULT 0,
    proximo_intento      TIMESTAMPTZ  NOT NULL,
    ultimo_error         VARCHAR(300),
    proveedor_mensaje_id VARCHAR(100),
    enviado_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_envios_estado   CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'FALLIDO', 'CANCELADO', 'OMITIDO')),
    CONSTRAINT chk_envios_tipo     CHECK (tipo_destinatario IN ('DOCENTE', 'COORDINADOR', 'COPIA')),
    CONSTRAINT chk_envios_copia    CHECK ((tipo_destinatario = 'COPIA') = (destinatario_id IS NULL)),
    CONSTRAINT chk_envios_intentos CHECK (intentos >= 0),
    CONSTRAINT uq_envios_destinatario
        UNIQUE NULLS NOT DISTINCT (notificacion_id, tipo_destinatario, destinatario_id)
);

CREATE INDEX IF NOT EXISTS ix_envios_pendientes
    ON public.notificaciones_envios (proximo_intento ASC, id ASC)
    WHERE estado = 'PENDIENTE';

CREATE INDEX IF NOT EXISTS ix_envios_notificacion_estado
    ON public.notificaciones_envios (notificacion_id, estado);
