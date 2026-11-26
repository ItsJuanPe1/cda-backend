-- =============================================================================
-- V1: Estructura académica base (Facultades, Unidades Académicas, Programas,
--     Asignaturas y Pensum)
-- Tablas: "Facultad", unidades_academicas, programas,
--         programa_unidades_academicas, asignaturas, pensum_academico
-- =============================================================================

-- 1. Facultades (nombre entre comillas respetando @Table(name = "\"Facultad\""))
-- El id corresponde al código SIRA de facultad (1..9); Psicología (códigos SIRA 402, 421, 422) usa id 10.
CREATE TABLE IF NOT EXISTS public."Facultad" (
    id          SERIAL       PRIMARY KEY,
    nombre      VARCHAR(150) NOT NULL,
    descripcion TEXT
);

INSERT INTO public."Facultad" (id, nombre) VALUES
    (1,  'Facultad de Ciencias Naturales y Exactas'),
    (2,  'Facultad de Humanidades'),
    (3,  'Facultad de Ciencias Sociales y Económicas'),
    (4,  'Facultad de Educación y Pedagogía'),
    (5,  'Facultad de Artes Integradas'),
    (6,  'Facultad de Salud'),
    (7,  'Facultad de Ingeniería'),
    (8,  'Facultad de Ciencias de la Administración'),
    (9,  'Otras Facultades / Institutos'),
    (10, 'Facultad de Psicología')
ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre;

SELECT setval(
    pg_get_serial_sequence('public."Facultad"', 'id'),
    COALESCE((SELECT MAX(id) FROM public."Facultad"), 1),
    TRUE
);

-- 2. Unidades Académicas (escuelas/departamentos: código de 3 dígitos, nombre y facultad)
CREATE TABLE IF NOT EXISTS public.unidades_academicas (
    codigo      VARCHAR(3)   PRIMARY KEY,
    nombre      VARCHAR(200) NOT NULL,
    id_facultad INTEGER      REFERENCES public."Facultad"(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_unidades_academicas_facultad ON public.unidades_academicas (id_facultad);

INSERT INTO public.unidades_academicas (codigo, nombre, id_facultad) VALUES
    ('102', 'BIOLOGÍA', 1),
    ('106', 'FÍSICA', 1),
    ('111', 'MATEMÁTICAS', 1),
    ('116', 'QUÍMICA', 1),
    ('201', 'HISTORIA', 2),
    ('202', 'ESCUELA DE ESTUDIOS LITERARIOS', 2),
    ('203', 'FILOSOFÍA', 2),
    ('204', 'ESCUELA DE CIENCIAS DEL LENGUAJE', 2),
    ('207', 'ESCUELA DE TRABAJO SOCIAL Y DESARROLLO HUMANO', 2),
    ('208', 'GEOGRAFÍA', 2),
    ('210', 'DEPARTAMENTO DE LINGÜÍSTICA Y FILOLOGÍA', 2),
    ('303', 'ECONOMÍA', 3),
    ('304', 'CIENCIAS SOCIALES', 3),
    ('401', 'EDUCACIÓN PRIMARIA', 4),
    ('403', 'ÁREA EDUCACIÓN EN CIENCIAS SOCIALES Y HUMANAS', 4),
    ('404', 'DEPARTAMENTO DE EDUCACION FISICA Y DEPORTES', 4),
    ('405', 'ÁREA EDUCACIÓN MATEMÁTICA', 4),
    ('406', 'ÁREA EDUCACIÓN EN CIENCIAS NATURALES', 4),
    ('410', 'ÁREA EDUCACIÓN, DESARROLLO Y COMUNIDAD', 4),
    ('411', 'ÁREA EDUCACIÓN, DESARROLLO Y COMUNIDAD', 4),
    ('412', 'DOCTORADO INTERINSTITUCIONAL', 4),
    ('415', 'ESCUELA DE EDUC. Y PEDAG. DE LAS CIENCIAS SOCIALES Y HUMANAS', 4),
    ('416', 'ESCUELA DE EDUCACIÓN, CULTURA Y COMUNIDAD', 4),
    ('417', 'ESCUELA DE EDUCACIÓN EN CIENCIAS, TECNOLOGÍAS Y CULTURAS', 4),
    ('402', 'PSICOLOGÍA', 10),
    ('421', 'DEPARTAMENTO DE ESTUDIOS PSICOSOCIALES', 10),
    ('422', 'DEPARTAMENTO DE CIENCIAS DEL DESARROLLO', 10),
    ('501', 'PROYECTOS', 5),
    ('502', 'TECNOLOGÍA DE LA CONSTRUCCIÓN', 5),
    ('503', 'ESTÉTICA', 5),
    ('504', 'ARTES ESCÉNICAS', 5),
    ('505', 'ESCUELA DE MÚSICA', 5),
    ('506', 'ESCUELA DE COMUNICACIÓN SOCIAL', 5),
    ('507', 'DISEÑO', 5),
    ('508', 'ARTES VISUALES', 5),
    ('509', 'DEPARTAMENTO DE ARTES VISUALES Y ESTÉTICA', 5),
    ('600', 'OFICINA EN EDUCACIÓN EN SALUD', 6),
    ('601', 'ESCUELA DE CIENCIAS BÁSICAS', 6),
    ('602', 'ESCUELA DE BACTERIOLOGÍA Y LABORATORIO CLÍNICO', 6),
    ('603', 'ESCUELA DE ENFERMERÍA', 6),
    ('604', 'MORFOLOGÍA', 6),
    ('605', 'CIENCIAS FISIOLÓGICAS', 6),
    ('606', 'PATOLOGÍA', 6),
    ('607', 'ESCUELA DE SALUD PUBLICA', 6),
    ('609', 'MEDICINA INTERNA', 6),
    ('610', 'CIRUGÍA', 6),
    ('611', 'PEDIATRÍA', 6),
    ('612', 'OBSTETRICIA Y GINECOLOGÍA', 6),
    ('613', 'PSIQUIATRÍA', 6),
    ('614', 'MICROBIOLOGÍA', 6),
    ('615', 'ANESTESIOLOGÍA', 6),
    ('617', 'MEDICINA FÍSICA', 6),
    ('618', 'ESTOMATOLOGÍA', 6),
    ('619', 'SALUD FAMILIAR', 6),
    ('620', 'ESCUELA DE REHABILITACIÓN HUMANA', 6),
    ('621', 'FISIOCINÉTICA HUMANA', 6),
    ('622', 'ESCUELA DE ODONTOLOGÍA', 6),
    ('623', 'FONOAUDIOLOGÍA', 6),
    ('624', 'OCUPACIÓN HUMANA', 6),
    ('626', 'ESCUELA DE MEDICINA', 6),
    ('700', 'OFICINA DE EXTENSIÓN Y DE INGENIERÍA', 7),
    ('701', 'VICEDECANATURA ACADÉMICA DE LA FACULTAD DE INGENIERÍA', 7),
    ('710', 'ESCUELA DE INGENIERÍA ELÉCTRICA Y ELECTRÓNICA', 7),
    ('720', 'ESCUELA DE INGENIERÍA CIVIL Y GEOMÁTICA', 7),
    ('730', 'ESCUELA DE INGENIERÍA DE RECURSOS NATURALES Y DEL AMBIENTE', 7),
    ('740', 'ESCUELA DE INGENIERÍA QUÍMICA', 7),
    ('745', 'ESCUELA DE INGENIERÍA DE ALIMENTOS', 7),
    ('750', 'ESCUELA DE INGENIERÍA DE SISTEMAS Y COMPUTACIÓN', 7),
    ('760', 'ESCUELA DE INGENIERÍA INDUSTRIAL', 7),
    ('761', 'ESCUELA DE ESTADÍSTICA', 7),
    ('770', 'ESCUELA DE INGENIERÍA DE MATERIALES', 7),
    ('780', 'ESCUELA DE INGENIERÍA MECÁNICA', 7),
    ('790', 'UNIVERSIDAD NACIONAL PALMIRA', 7),
    ('801', 'ADMINISTRACIÓN Y ORGANIZACIONES', 8),
    ('802', 'CONTABILIDAD Y FINANZAS', 8),
    ('803', 'PERTENECE AL DEPARTAMENTO 801', 8),
    ('804', 'PERTENECE AL DEPARTAMENTO 801', 8),
    ('806', 'PERTENECE AL DEPARTAMENTO 801', 8),
    ('807', 'PERTENECE AL DEPARTAMENTO 802', 8),
    ('901', 'DEPARTAMENTO DE ALTOS ESTUDIOS JURÍDICOS', 9),
    ('905', 'ESTUDIOS DEL PACÍFICO / OTRAS UNIDADES', 9),
    ('910', 'PASANTÍAS', 9),
    ('920', 'POSGRADOS INTERINSTITUCIONALES', 9),
    ('940', 'PROGRAMA DE NIVELACIÓN Y TALENTOS', 9),
    ('950', 'DIRECCIÓN DE REGIONALIZACIÓN', 9),
    ('990', 'TODAS LAS FACULTADES', 9),
    ('999', 'TODAS LAS FACULTADES', 9)
ON CONFLICT (codigo) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    id_facultad = EXCLUDED.id_facultad;

-- 3. Programas académicos (carreras)
CREATE TABLE IF NOT EXISTS public.programas (
    code_program         VARCHAR(50)  PRIMARY KEY,
    name_program         VARCHAR(100) NOT NULL,
    numero_semestre      INTEGER      NOT NULL,
    id_facultad_programa INTEGER      REFERENCES public."Facultad"(id) ON DELETE RESTRICT,
    jornada              VARCHAR(10)  NOT NULL DEFAULT 'DIURNA',
    ingreso              VARCHAR(10)  NOT NULL DEFAULT 'SEMESTRAL',
    estado               VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
    apertura             VARCHAR(10),
    numero_resolucion    VARCHAR(50),
    CONSTRAINT chk_programas_jornada        CHECK (jornada IN ('DIURNA', 'NOCTURNA', 'AMBAS')),
    CONSTRAINT chk_programas_ingreso        CHECK (ingreso IN ('SEMESTRAL', 'ANUAL')),
    CONSTRAINT chk_programas_estado         CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    CONSTRAINT chk_programas_duracion       CHECK (numero_semestre BETWEEN 1 AND 20),
    CONSTRAINT chk_programas_codigo_jornada CHECK (
        (code_program LIKE '%-NOC' AND jornada = 'NOCTURNA')
        OR (code_program LIKE '%-DIU' AND jornada = 'DIURNA')
        OR (code_program NOT LIKE '%-NOC' AND code_program NOT LIKE '%-DIU')
    ),
    CONSTRAINT chk_programas_apertura       CHECK (apertura IS NULL OR apertura IN ('SEMESTRAL', 'ANUAL'))
);

CREATE INDEX IF NOT EXISTS idx_programas_facultad ON public.programas (id_facultad_programa);
CREATE INDEX IF NOT EXISTS idx_programas_estado   ON public.programas (estado);

-- 4. Relación programas <-> unidades académicas (banco de electivas profesionales)
CREATE TABLE IF NOT EXISTS public.programa_unidades_academicas (
    code_program            VARCHAR(50) NOT NULL REFERENCES public.programas(code_program) ON DELETE CASCADE,
    codigo_unidad_academica VARCHAR(3)  NOT NULL REFERENCES public.unidades_academicas(codigo),
    propia                  BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (code_program, codigo_unidad_academica)
);

-- Seed condicional idempotente si ya existen programas cargados (cubre códigos con y sin guion)
INSERT INTO public.programa_unidades_academicas (code_program, codigo_unidad_academica, propia)
SELECT p.code_program, v.unidad, v.propia
FROM public.programas p
JOIN (VALUES
    ('2724', '750', true),
    ('2725', '710', true),
    ('2728', '730', true),
    ('2726', '745', true),
    ('3751', '760', true),
    ('2722', '780', true),
    ('2135', '730', true),
    ('3846', '801', true),
    ('3846', '802', false),
    ('2839', '801', true),
    ('2839', '802', false),
    ('3841', '802', true),
    ('3841', '801', false)
) AS v(base, unidad, propia) ON (p.code_program = v.base OR p.code_program LIKE v.base || '-%')
ON CONFLICT (code_program, codigo_unidad_academica) DO NOTHING;

-- 5. Catálogo maestro de asignaturas (relacionadas a unidades académicas)
CREATE TABLE IF NOT EXISTS public.asignaturas (
    code_course             VARCHAR(50)  PRIMARY KEY,
    name_course             VARCHAR(150) NOT NULL,
    credits                 INTEGER      NOT NULL DEFAULT 0,
    is_elective             BOOLEAN      DEFAULT FALSE,
    es_complementaria       BOOLEAN      DEFAULT FALSE,
    es_basica               BOOLEAN      DEFAULT FALSE,
    codigo_unidad_academica VARCHAR(3)   REFERENCES public.unidades_academicas(codigo) ON DELETE RESTRICT,
    activo                  BOOLEAN      NOT NULL DEFAULT TRUE,
    horas_semanales         INTEGER,
    CONSTRAINT ck_asignaturas_horas CHECK (horas_semanales IS NULL OR horas_semanales BETWEEN 0 AND 20)
);

CREATE INDEX IF NOT EXISTS idx_asignaturas_unidad_academica ON public.asignaturas (codigo_unidad_academica);
CREATE INDEX IF NOT EXISTS idx_asignaturas_activo           ON public.asignaturas (activo);

-- 6. Mallas curriculares versionadas (pensum académico)
CREATE TABLE IF NOT EXISTS public.pensum_academico (
    code_program         VARCHAR(50)  NOT NULL REFERENCES public.programas(code_program) ON DELETE RESTRICT,
    code_course          VARCHAR(50)  NOT NULL REFERENCES public.asignaturas(code_course) ON DELETE RESTRICT,
    version              INTEGER      NOT NULL DEFAULT 1,
    name_course_snapshot VARCHAR(150),
    semestre             INTEGER      NOT NULL,
    is_elective          BOOLEAN      DEFAULT FALSE,
    facultad             VARCHAR(150),
    activo               BOOLEAN      NOT NULL DEFAULT TRUE,
    vigente              BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_desactivacion  TIMESTAMP,
    fecha_reactivacion   TIMESTAMP,
    numero_resolucion    VARCHAR(50),
    numero_semestres     INTEGER,
    editada              BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_edicion        TIMESTAMP,
    PRIMARY KEY (code_program, code_course, version)
);

CREATE INDEX IF NOT EXISTS idx_pensum_programa_vigente ON public.pensum_academico (code_program, vigente);
CREATE INDEX IF NOT EXISTS idx_pensum_code_course      ON public.pensum_academico (code_course);
