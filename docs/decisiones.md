# Registro de Decisiones de Arquitectura y Negocio

Este documento registra las decisiones acordadas con el equipo y confirmadas por el usuario, conforme a las fuentes de verdad y orden de precedencia del proyecto.

---

## [2026-10-23] Aprobación de Contrato y Flujo de Login para Coordinador (HU-010)

* **HU afectadas:** HU-010 (Login de Coordinador), HU-001 (Login de Administrador), HU-006 (Login de Docente).
* **Decisión:**
  1. Se aprueba la especificación del contrato en `docs/contratos/HU-010.md`.
  2. El endpoint de autenticación `POST /api/auth/google` soporta el parámetro opcional `rolEsperado` (`1`: Admin, `2`: Coordinador, `3`: Docente) y auto-detección si no se especifica.
  3. Para el rol de coordinador (`rol = 2`):
     - Valida pertenencia al dominio `@correounivalle.edu.co`.
     - Si el coordinador no existe en `coordinadores`, pero el correo pertenece a otro rol (admin o docente), responde con error 403 y mensaje `"No tiene perfil de coordinador"` (Criterio 3.3).
     - Si no existe en ninguna tabla, responde con error 403 y mensaje `"Su cuenta no está registrada. Contacte al administrador"` (Criterio 2.2).
     - Si existe pero está inactivo (`activo = false`), responde con error 403 y mensaje `"Su perfil está desactivado. Contacte al administrador"` (Criterio 2.3).
     - Si está activo y no tiene programa asignado (`programa == null`), permite el inicio de sesión emitiendo el JWT con rol 2 y mensaje `"Aún no tiene un programa asignado. Contacte al administrador"` (Criterio 3.4).
     - Si está activo y tiene programa asignado, responde con rol 2, `codeProgram`, `nombrePrograma` y mensaje `"Inicio de sesión exitoso"` (Criterio 1.3).
  4. Se crea el endpoint `GET /api/coordinadores/me` protegido por Spring Security (`ROLE_COORDINADOR`, `ROLE_ADMIN`) para obtener el perfil del coordinador a partir de su JWT autenticado, eliminando la vulnerabilidad de pasar correos por query param.


## [2026-10-25] Alcance de edición del perfil docente (HU-015)

* **HU afectadas:** HU-015 (Editar mi perfil), HU-022 (Perfil docente).
* **Decisión:**
  1. El docente **no puede editar sus nombres ni apellidos** desde su perfil (se ajusta el criterio 2 de HU-015; el criterio 3 deja de aplicar).
  2. Todas las acciones de HU-015 se realizan desde el módulo **«Mi perfil»**.
  3. El **correo institucional no es editable** por el docente (es su credencial de Google, RN-14). Los criterios 4 y 9 de HU-015 no aplican al docente.
  4. Se agrega el **correo personal** al docente como dato de contacto editable desde «Mi perfil».
  5. El docente puede editar su **teléfono** (dato de contacto, RN-08).
  6. La **foto** se guarda en una tabla aparte de la base de datos, con un tamaño máximo de **5 MB** (JPG o PNG).
  7. **Correo personal:** opcional, formato válido, máximo 100 caracteres y no puede terminar en `@correounivalle.edu.co`. Mensajes: «El correo personal no tiene un formato válido» y «El correo personal no puede ser institucional».
  8. **Teléfono:** opcional, solo dígitos, de 7 a 10. Mensaje: «El teléfono solo admite de 7 a 10 dígitos».
  9. **Eliminar la foto la desactiva** (RN-13). Solo el administrador la elimina directamente en la base de datos; no hay endpoint de borrado físico.
  10. **Una sola foto almacenada por docente:** al subir una nueva (o la primera) se reemplaza el contenido de la fila existente y queda activa; solo hay una activa.
  11. Contrato `docs/contratos/HU-015.md` aprobado. La migración de HU-015 se numera **V14** para no chocar con la V13 ya aplicada en la base compartida.


## [2026-10-27] Sincronización con el product backlog actualizado

* **HU afectadas:** HU-010, HU-015, HU-017, HU-019, HU-037, HU-040 (cambios de texto) y las HU con cambio de estado listadas abajo.
* **Cambios en `docs/Roses_corp_HU.csv`:**
  1. HU-010: se quita «(supuesto)» del criterio 3.4.
  2. HU-015: el docente edita foto, correo personal y teléfono; nombres, apellidos y correo institucional no son editables (alinea el CSV con la decisión del 2026-10-25). Los criterios 4 y 9 siguen escritos para el correo institucional (inconsistencia 3 de CLAUDE.md).
  3. HU-017: se aclara el significado de ingreso semestral o anual en el criterio 1; se quita «horas de estudio independiente» del criterio 2.1 (pendiente de confirmar, inconsistencia 10).
  4. HU-019: Observaciones pasa de «5» a «5.0» (pendiente de confirmar, inconsistencia 11).
  5. HU-037: «programas» pasa a «programas académicos».
  6. HU-040: «mallas» pasa a «mallas curriculares».
  7. Los errores de tipeo detectados (HU-015 «telefono»; HU-017 «cada cuanto», «sujeto», «tendria», «esten») se dejan como están por decisión del equipo; se corrigen en el Excel.
* **Estado de las HU (confirmado por el equipo):**
  - **Hechas:** HU-001, HU-002, HU-003, HU-005, HU-010, HU-015, HU-022, HU-026, HU-027, HU-028, HU-029, HU-031.
  - **Pendientes:** HU-004, HU-007.
  - El resto sigue «En Backlog».
* **Otros:** se agrega `*.csv text eol=lf` a `.gitattributes`.


## [2026-10-27] Programas académicos: jornada, duración, código y desactivación (HU-040)

* **HU afectadas:** HU-040 (crear y editar programas), HU-041 (desactivar o eliminar), HU-017 y HU-032 (mallas), HU-042 y HU-045 (cohortes y programación).
* **Decisión:**
  1. La resolución de calendario es distinta de la resolución de programa (también llamada programa académico o malla curricular).
  2. La malla curricular es la misma para el programa en jornada diurna, nocturna o ambas. Cambia la duración: por ejemplo 6 semestres en diurna y 7 en nocturna, o 10 en diurna y hasta 11 en nocturna, porque algunas asignaturas se redistribuyen para dar un semestre extra.
  3. Todos los programas tienen código de 4 dígitos. Los valores actuales con sufijo de jornada en `programas.code_program` (por ejemplo `2135-DIU`, `3846-DIU`) se deben llevar a 4 dígitos.
  4. Un programa no se elimina: se desactiva (RN-13). El `DELETE` de programas deja de borrar físicamente.
  5. Una asignatura de la malla puede ubicarse en un semestre distinto según la jornada del programa (diurna o nocturna). La malla sigue siendo una sola; lo que varía por jornada es el semestre de cada asignatura. Se configura desde el inicio, al registrar la malla, y se implementa en HU-017 y HU-032 con su propia migración.
  6. La rotación de cohortes (HU-043) no depende del tipo de ingreso: cada cohorte c pasa a c+1 y la del último semestre vuelve al 1, igual para programas anuales y semestrales. El campo `ingreso` (semestral o anual) es descriptivo.
  7. Valores iniciales en V15: todos los programas existentes quedan con jornada diurna e ingreso semestral; el administrador los corrige desde la pantalla de HU-040.
  8. Administración de Empresas (3846) queda con jornada ambas: 10 semestres en diurna y 11 en nocturna. Sirve de guía para otros programas de pregrado que se registren con las dos jornadas (nocturna con un semestre más que la diurna, editable).
  9. Se conserva `numero_semestre` como duración en jornada diurna y se agrega `semestres_nocturna`.
  10. Migración V15 (`V15__hu040_programas_codigo_jornada_estado.sql`) aprobada para crear y probar en PostgreSQL 17 local. Aún no se aplica en la base compartida.
  11. No se bloquea reducir la duración de un programa por debajo del último semestre de su malla: si la carrera pasa, por ejemplo, de 10 a 8 semestres, después se ajusta la malla (HU-032) o se registra una nueva (HU-017), redistribuyendo o retirando asignaturas.
  12. Contrato `docs/contratos/HU-040.md` aprobado: el `DELETE` desactiva, el `PUT` es parcial y el código no se edita.


## [2026-10-29] Catálogo de asignaturas con código y nombre únicos (HU-044)

* **HU afectadas:** HU-044 (catálogo de asignaturas), HU-017, HU-032 y HU-033 (mallas curriculares), HU-045 (generación de programación).
* **Origen:** implementado por el equipo en `develop` (commit `13d0f41`, PR #35). Se registra a partir del código; no hay contrato `docs/contratos/HU-044.md` ni ficha `docs/hu/HU-044.md`.
* **Decisión:**
  1. **Código de asignatura:** alfanumérico, de 1 a 8 caracteres, se guarda en mayúsculas sin espacios. Mensaje: «El código debe ser alfanumérico y tener entre 1 y 8 caracteres».
  2. **Nombre de asignatura:** obligatorio, máximo 100 caracteres, único sin distinguir mayúsculas ni espacios al inicio o al final. Mensajes: «Ya existe una asignatura con el código: …» y «Ya existe una asignatura con el nombre: …».
  3. **Sin borrado físico (RN-13):** `DELETE /api/asignaturas/{codeCourse}` desactiva la asignatura (`activo = false`) y se conserva por compatibilidad con clientes antiguos.
  4. **Asignaturas inactivas:** no se pueden agregar a una malla nueva. Mensaje: «La asignatura … está inactiva y no se puede agregar a una nueva malla».
  5. **Nombre histórico en la malla:** cada fila de `pensum_academico` guarda `name_course_snapshot` con el nombre que tenía la asignatura al registrar o actualizar esa versión de la malla. Así, si después se renombra la asignatura en el catálogo, las versiones anteriores de la malla conservan el nombre original.
  6. **Migración V22** (`V22__hu044_snapshot_nombre_asignatura_en_pensum.sql`): agrega `name_course_snapshot VARCHAR(150)`, lo llena con el nombre actual de cada asignatura (los nombres anteriores a la migración no se pueden reconstruir) y cambia la llave foránea `pensum_academico → asignaturas` a `ON DELETE RESTRICT`.
* **Pendientes de confirmar con el equipo:**
  - Los duplicados responden 400 (`BadRequestException`). El proyecto ya tiene `ConflictException` (409); hay que definir cuál se usa.
  - `name_course_snapshot` admite 150 caracteres, pero el catálogo limita el nombre a 100, porque hay nombres históricos de hasta 150. Confirmar si se acepta esta diferencia.
  - Falta escribir el contrato `docs/contratos/HU-044.md`.
