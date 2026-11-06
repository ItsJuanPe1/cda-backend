-- HU-058: bitácora de cambios e ingresos (solo inserción y consulta)
CREATE TABLE bitacora (
    id               BIGSERIAL PRIMARY KEY,
    fecha            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    usuario_correo   VARCHAR(150),
    usuario_rol      VARCHAR(20),
    operador_nombre  VARCHAR(150),
    accion           VARCHAR(30)  NOT NULL,
    entidad          VARCHAR(50)  NOT NULL,
    entidad_id       VARCHAR(100),
    periodo_id       BIGINT REFERENCES periodos_academicos (id) ON DELETE RESTRICT,
    valor_anterior   JSONB,
    valor_nuevo      JSONB,
    resultado        VARCHAR(10),
    motivo           VARCHAR(60),
    CONSTRAINT ck_bitacora_rol       CHECK (usuario_rol IS NULL OR usuario_rol IN ('ADMIN','COORDINADOR','DOCENTE')),
    CONSTRAINT ck_bitacora_resultado CHECK (resultado IS NULL OR resultado IN ('ACEPTADO','RECHAZADO')),
    CONSTRAINT ck_bitacora_ingreso   CHECK ((accion = 'INGRESO') = (resultado IS NOT NULL))
);

CREATE INDEX ix_bitacora_fecha   ON bitacora (fecha DESC, id DESC);
CREATE INDEX ix_bitacora_usuario ON bitacora (lower(usuario_correo));
CREATE INDEX ix_bitacora_entidad ON bitacora (entidad, entidad_id);
CREATE INDEX ix_bitacora_periodo ON bitacora (periodo_id);

-- Trigger (C4, RN-13): la base rechaza UPDATE, DELETE y TRUNCATE sobre la bitácora,
-- aunque alguien intente hacerlo fuera de la aplicación.
CREATE OR REPLACE FUNCTION fn_bitacora_solo_insercion() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'La bitácora es de solo lectura: operación % no permitida', TG_OP;
END;
$$;

CREATE TRIGGER trg_bitacora_no_update_delete
    BEFORE UPDATE OR DELETE ON bitacora
    FOR EACH ROW EXECUTE FUNCTION fn_bitacora_solo_insercion();

CREATE TRIGGER trg_bitacora_no_truncate
    BEFORE TRUNCATE ON bitacora
    FOR EACH STATEMENT EXECUTE FUNCTION fn_bitacora_solo_insercion();
