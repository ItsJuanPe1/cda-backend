-- Acceso extraordinario: el administrador habilita a un docente un plazo para editar su
-- disponibilidad fuera de la ventana general, sin cambiar el calendario del período.
-- Una fila por acceso; "vigente" = no revocado y entre fecha_inicio y fecha_fin.
CREATE TABLE accesos_extraordinarios_docente (
    id            BIGSERIAL PRIMARY KEY,
    id_docente    BIGINT       NOT NULL REFERENCES docentes (id) ON DELETE RESTRICT,
    fecha_inicio  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_fin     TIMESTAMPTZ  NOT NULL,
    motivo        VARCHAR(500) NOT NULL,
    otorgado_por  VARCHAR(150) NOT NULL,
    revocado_en   TIMESTAMPTZ,
    revocado_por  VARCHAR(150),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_acceso_extra_fechas     CHECK (fecha_fin > fecha_inicio),
    CONSTRAINT ck_acceso_extra_motivo     CHECK (length(btrim(motivo)) > 0),
    CONSTRAINT ck_acceso_extra_revocacion CHECK ((revocado_en IS NULL) = (revocado_por IS NULL))
);

CREATE INDEX ix_acceso_extra_docente ON accesos_extraordinarios_docente (id_docente, fecha_fin DESC);