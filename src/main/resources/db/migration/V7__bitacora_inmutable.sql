-- =============================================================================
-- V7: Bitácora inmutable de ingresos y cambios de negocio (HU-058)
-- Tabla: bitacora
-- Triggers: trg_bitacora_no_update_delete, trg_bitacora_no_truncate
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.bitacora (
    id              BIGSERIAL    PRIMARY KEY,
    fecha           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario_correo  VARCHAR(150),
    usuario_rol     VARCHAR(20),
    operador_nombre VARCHAR(150),
    accion          VARCHAR(30)  NOT NULL,
    entidad         VARCHAR(50)  NOT NULL,
    entidad_id      VARCHAR(100),
    periodo_id      BIGINT       REFERENCES public.periodos_academicos(id) ON DELETE RESTRICT,
    valor_anterior  JSONB,
    valor_nuevo     JSONB,
    resultado       VARCHAR(10),
    motivo          VARCHAR(60),
    CONSTRAINT ck_bitacora_rol       CHECK (usuario_rol IS NULL OR usuario_rol IN ('ADMIN', 'COORDINADOR', 'DOCENTE')),
    CONSTRAINT ck_bitacora_resultado CHECK (resultado IS NULL OR resultado IN ('ACEPTADO', 'RECHAZADO')),
    CONSTRAINT ck_bitacora_ingreso   CHECK ((accion = 'INGRESO') = (resultado IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS ix_bitacora_fecha   ON public.bitacora (fecha DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_bitacora_usuario ON public.bitacora (LOWER(usuario_correo));
CREATE INDEX IF NOT EXISTS ix_bitacora_entidad ON public.bitacora (entidad, entidad_id);
CREATE INDEX IF NOT EXISTS ix_bitacora_periodo ON public.bitacora (periodo_id);

-- Garantía de inmutabilidad en base de datos (solo INSERT y SELECT)
CREATE OR REPLACE FUNCTION public.fn_bitacora_solo_insercion()
RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'La bitácora es de solo lectura: operación % no permitida', TG_OP;
END;
$$;

DROP TRIGGER IF EXISTS trg_bitacora_no_update_delete ON public.bitacora;
CREATE TRIGGER trg_bitacora_no_update_delete
    BEFORE UPDATE OR DELETE ON public.bitacora
    FOR EACH ROW EXECUTE FUNCTION public.fn_bitacora_solo_insercion();

DROP TRIGGER IF EXISTS trg_bitacora_no_truncate ON public.bitacora;
CREATE TRIGGER trg_bitacora_no_truncate
    BEFORE TRUNCATE ON public.bitacora
    FOR EACH STATEMENT EXECUTE FUNCTION public.fn_bitacora_solo_insercion();
