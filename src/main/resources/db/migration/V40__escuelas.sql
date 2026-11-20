-- Escuelas (por prefijo del código de la asignatura) y escuelas cuyas electivas profesionales
-- puede programar cada programa. Un programa sin filas aquí sigue viendo las electivas por facultad.

CREATE TABLE IF NOT EXISTS public.escuelas (
    codigo VARCHAR(3)   PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL
);

INSERT INTO public.escuelas (codigo, nombre) VALUES
    ('102', 'ESCUELA DE BIOLOGÍA'),
    ('106', 'FÍSICA'),
    ('111', 'MATEMÁTICAS'),
    ('116', 'QUÍMICA'),
    ('201', 'HISTORIA'),
    ('202', 'ESCUELA DE ESTUDIOS LITERARIOS'),
    ('204', 'ESCUELA DE CIENCIAS DEL LENGUAJE'),
    ('210', 'DEPARTAMENTO DE LINGÜÍSTICA Y FILOLOGÍA'),
    ('303', 'ECONOMÍA'),
    ('304', 'CIENCIAS SOCIALES'),
    ('404', 'DEPARTAMENTO DE EDUCACIÓN FÍSICA Y DEPORTES'),
    ('415', 'ESCUELA DE EDUC. Y PEDAG. DE LAS CIENCIAS SOCIALES Y HUMANAS'),
    ('416', 'ESCUELA DE EDUCACIÓN, CULTURA Y COMUNIDAD'),
    ('417', 'ESCUELA DE EDUCACIÓN EN CIENCIAS, TECNOLOGÍAS Y CULTURAS'),
    ('421', 'DEPARTAMENTO DE ESTUDIOS PSICOSOCIALES'),
    ('422', 'DEPARTAMENTO DE CIENCIAS DEL DESARROLLO'),
    ('504', 'ARTES ESCÉNICAS'),
    ('506', 'ESCUELA DE COMUNICACIÓN SOCIAL'),
    ('507', 'DISEÑO'),
    ('603', 'ESCUELA DE ENFERMERÍA'),
    ('604', 'MORFOLOGÍA'),
    ('605', 'CIENCIAS FISIOLÓGICAS'),
    ('701', 'VICEDECANATURA ACADÉMICA DE LA FACULTAD DE INGENIERÍA'),
    ('710', 'ESCUELA DE INGENIERÍA ELÉCTRICA Y ELECTRÓNICA'),
    ('730', 'ESCUELA DE INGENIERÍA DE RECURSOS NATURALES Y DEL AMBIENTE'),
    ('745', 'ESCUELA DE INGENIERÍA DE ALIMENTOS'),
    ('750', 'ESCUELA DE INGENIERÍA DE SISTEMAS Y COMPUTACIÓN'),
    ('760', 'ESCUELA DE INGENIERÍA INDUSTRIAL'),
    ('761', 'ESCUELA DE ESTADÍSTICA'),
    ('780', 'ESCUELA DE INGENIERÍA MECÁNICA'),
    ('801', 'ADMINISTRACIÓN Y ORGANIZACIONES'),
    ('802', 'CONTABILIDAD Y FINANZAS'),
    ('950', 'DIRECCIÓN DE REGIONALIZACIÓN'),
    ('990', 'TODAS LAS FACULTADES'),
    ('999', 'TODAS LAS FACULTADES')
ON CONFLICT (codigo) DO NOTHING;

-- propia = true: escuela del programa; false: escuela adicional cuyas electivas también puede programar
CREATE TABLE IF NOT EXISTS public.programa_escuelas (
    code_program   VARCHAR(50) NOT NULL REFERENCES public.programas (code_program) ON DELETE CASCADE,
    codigo_escuela VARCHAR(3)  NOT NULL REFERENCES public.escuelas (codigo),
    propia         BOOLEAN     NOT NULL DEFAULT false,
    PRIMARY KEY (code_program, codigo_escuela)
);

-- El código base (antes del guion) cubre las dos jornadas: 2724-DIU y 2724-NOC.
INSERT INTO public.programa_escuelas (code_program, codigo_escuela, propia)
SELECT p.code_program, v.escuela, v.propia
FROM public.programas p
JOIN (VALUES
    ('2724', '750', true),   -- Tecnología en Desarrollo de Software
    ('2725', '710', true),   -- Tecnología en Electrónica Industrial
    ('2728', '730', true),   -- Tecnología en Producción Agroambiental
    ('2726', '745', true),   -- Tecnología de Procesamiento de Alimentos
    ('3751', '760', true),   -- Ingeniería Industrial
    ('2722', '780', true),   -- Tecnología en Mantenimiento de Sistemas Electromecánicos
    ('2135', '730', true),   -- Manejo de Producción Agroforestal
    ('3846', '801', true),   -- Administración de Empresas
    ('3846', '802', false),
    ('2839', '801', true),   -- Tecnología en Gestión Logística
    ('2839', '802', false),
    ('3841', '802', true),   -- Contaduría Pública
    ('3841', '801', false)
) AS v(base, escuela, propia) ON p.code_program LIKE v.base || '-%'
ON CONFLICT (code_program, codigo_escuela) DO NOTHING;
