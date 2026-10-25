CREATE TABLE periodos (
    id                          BIGSERIAL PRIMARY KEY,
    nombre                      VARCHAR(100)  NOT NULL,
    estado                      VARCHAR(20)   NOT NULL,
    numero_resolucion           VARCHAR(100),
    enlace_resolucion           VARCHAR(500),
    inicio_semestre             DATE          NOT NULL,
    fin_semestre                DATE          NOT NULL,
    fecha_adicion               DATE,
    fecha_cancelacion           DATE,
    -- DEPENDE-HU:plazos (su estructura final depende de otra HU)
    plazo_docentes_inicio       TIMESTAMPTZ,
    plazo_docentes_fin          TIMESTAMPTZ,
    plazo_coordinadores_inicio  TIMESTAMPTZ,
    plazo_coordinadores_fin     TIMESTAMPTZ,
    fecha_desactivacion         TIMESTAMPTZ,
    CONSTRAINT ck_periodos_estado CHECK (estado IN ('VIGENTE', 'HISTORICO', 'DESACTIVADO')),
    CONSTRAINT ck_periodos_semestre CHECK (fin_semestre >= inicio_semestre)
);

-- A lo sumo un período VIGENTE a la vez.
CREATE UNIQUE INDEX ux_periodos_un_solo_vigente ON periodos (estado) WHERE estado = 'VIGENTE';

CREATE INDEX ix_periodos_inicio_semestre ON periodos (inicio_semestre DESC);