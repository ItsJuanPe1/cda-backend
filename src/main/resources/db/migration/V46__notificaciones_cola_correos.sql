-- HU-038: cola de correos persistente, reutilizable por HU-039, HU-055 y HU-057.
-- Un evento (notificaciones) se programa con el período; al llegar su hora se resuelven los
-- destinatarios y se crea un envío por destinatario y uno de copia (notificaciones_envios).
-- Sin borrado físico (RN-13): eventos y envíos se cancelan, no se eliminan.
-- Fechas en UTC (TIMESTAMPTZ). Sin datos personales: el correo se resuelve al enviar.

CREATE TABLE notificaciones (
    id                  BIGSERIAL PRIMARY KEY,
    tipo_evento         VARCHAR(50)  NOT NULL,
    periodo_id          BIGINT       REFERENCES periodos_academicos(id) ON DELETE RESTRICT,
    fecha_programada    TIMESTAMPTZ  NOT NULL,
    estado              VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    datos               JSONB,
    motivo_cancelacion  VARCHAR(100),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    procesada_at        TIMESTAMPTZ,
    cancelada_at        TIMESTAMPTZ,
    CONSTRAINT chk_notificaciones_estado CHECK (estado IN ('PENDIENTE', 'PROCESADO', 'CANCELADO'))
);

COMMENT ON TABLE notificaciones IS 'HU-038: eventos de correo programados (apertura, recordatorio, actualización)';
COMMENT ON COLUMN notificaciones.datos IS 'Fechas del plazo antes y después del cambio; sin datos personales';

CREATE INDEX idx_notificaciones_pendientes ON notificaciones (estado, fecha_programada);
CREATE INDEX idx_notificaciones_periodo    ON notificaciones (periodo_id, estado);

CREATE TABLE notificaciones_envios (
    id                   BIGSERIAL PRIMARY KEY,
    notificacion_id      BIGINT       NOT NULL REFERENCES notificaciones(id) ON DELETE RESTRICT,
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
    CONSTRAINT uq_envios_destinatario UNIQUE NULLS NOT DISTINCT (notificacion_id, tipo_destinatario, destinatario_id)
);

COMMENT ON TABLE notificaciones_envios IS 'HU-038: un envío por destinatario y uno de copia por evento';
COMMENT ON COLUMN notificaciones_envios.destinatario_id IS 'docentes.id; NULL en la copia. El correo se resuelve al enviar';
COMMENT ON COLUMN notificaciones_envios.ultimo_error IS 'Motivo del fallo, sin correos, nombres ni tokens';
COMMENT ON COLUMN notificaciones_envios.proveedor_mensaje_id IS 'Id del mensaje devuelto por Gmail API';

CREATE INDEX idx_envios_pendientes   ON notificaciones_envios (estado, proximo_intento);
CREATE INDEX idx_envios_notificacion ON notificaciones_envios (notificacion_id);
