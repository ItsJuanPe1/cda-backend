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


## [2026-10-29] Ver programas académicos y sus coordinadores (HU-037)

* **HU afectadas:** HU-037; relacionadas HU-020 (asignar coordinador) y HU-036 (reemplazar coordinador).
* **Decisión:**
  1. «Programas con resolución activa» (criterio 1) se entiende como los **programas de pregrado activos**. Todos los programas del sistema son de pregrado; no se agrega un campo de nivel. Se muestran los programas con `estado = ACTIVO`.
  2. Si el coordinador asignado a un programa está **desactivado**, la fila muestra «Sin coordinador» y el botón «Asignar coordinador».
  3. La **búsqueda y el filtro** «Sin coordinador» se hacen en el **servidor**, sin paginación.
  4. Se trabaja en la rama `feature10-sanabria`.
  5. No requiere migración.


## [2026-11-03] Histórico de asignaturas dictadas por docente (HU-049)

* **HU afectadas:** HU-049 (asociar docentes a asignaturas), HU-026 (docentes), HU-040 (programas), HU-017/HU-032 (mallas), HU-045 (programación).
* **Decisión:**
  1. El «perfil» del docente se interpreta con la información disponible: una asignatura corresponde a su perfil si (a) tiene la misma facultad del docente o (b) está en la malla registrada de algún programa de su facultad. Si el docente no tiene facultad ni programa, no se puede validar el perfil y la asociación se rechaza.
  2. La asociación se guarda en la tabla nueva `docente_asignatura` con `docente_id`, `code_course`, `id_facultad`, periodo opcional, origen (`MANUAL`, `PROGRAMACION`), estado y fechas. La llave única es `(docente_id, code_course, id_facultad)`. La facultad se deriva del docente; el administrador no la elige.
  3. **Sin borrado físico (RN-13):** quitar una asociación la desactiva (`activo = false`, `desactivado_at`). Se puede reactivar; el histórico se conserva.
  4. **No hay carga inicial.** El alcance se simplifica a la asociación manual y a la alimentación automática desde programación. El frontend permite seleccionar varias asignaturas de la facultad del docente y las envía una por una.
  5. El origen `MANUAL` se usa para las asociaciones del administrador y `PROGRAMACION` cuando el docente queda asignado a un curso de un periodo. Las filas antiguas con origen `CARGA_INICIAL` se migran a `MANUAL` en V28.
  6. **Criterio 4:** al generar o regenerar la programación, `ProgramacionService` registra en el histórico los cursos que tengan docente. Además, la migración V27 crea el trigger `trg_curso_docente_historico` sobre `cursos_programacion` y V28 lo actualiza para usar facultad. Ambos caminos son idempotentes por la llave única.
  7. La migración V27 hace backfill desde `cursos_programacion`; V28 migra la columna `code_program` a `id_facultad`, elimina el programa del histórico y consolida en una sola fila las asociaciones duplicadas de programas de la misma facultad.
  8. Los endpoints nuevos son de uso exclusivo del administrador y viven bajo `/api/docentes-asignaturas`. La consulta acepta filtros por docente, asignatura, facultad y estado (criterio 5).
  9. El contrato se documenta en `docs/contratos/HU-049.md`.
* **Pendiente de confirmar con el equipo:**
  - Si se requiere conservar el programa original como dato informativo del histórico. Hoy la facultad es la unidad de asociación y el programa solo se conserva en `cursos_programacion`.


## [2026-11-06] Se adelanta la bitácora de cambios (HU-058)

* **HU afectadas:** HU-058 (bitácora), HU-047 (revisar y ajustar la programación).
* **Decisión:**
  1. HU-058 no tenía sprint asignado; se adelanta porque HU-047 (criterio 5: «Cada ajuste queda en la bitácora») depende de ella.
  2. La bitácora se implementa como un **servicio reutilizable** (`BitacoraService.registrar(...)`) que cualquier servicio llama dentro de su misma transacción, además de la pantalla de consulta del administrador.
  3. Si falla el registro en la bitácora, falla la operación: no se permiten ajustes sin rastro.
  4. La bitácora es de solo inserción y consulta desde la aplicación (criterio 4 y RN-13).
  5. HU-047 no se toca en esta HU.
  6. **Persona que operó la cuenta compartida:** se registra el `nombreCompleto` del coordinador asignado al programa (campo `operador_nombre`), copiado al momento del registro. No se pide el nombre al iniciar sesión.
  7. **Rechazo por fuera de fechas (RN-15):** se deja definido el motivo `FUERA_DE_VENTANA`, pero la validación de ventanas en el login no se implementa en HU-058; se hará con RN-15/HU-055.
  8. **Filtro «periodo»:** es el periodo académico (`periodos_academicos`). Cada servicio envía `periodo_id` cuando la entidad pertenece a un periodo; los ingresos lo dejan en `null`.
  9. Contrato `docs/contratos/HU-058.md` y migración V29 (`V29__hu058_create_bitacora.sql`) aprobados. La V29 se probó en PostgreSQL 17.11 local; aún no se aplica en la base compartida.
  10. Si falla el registro de un ingreso **aceptado**, no se emite la sesión. Si falla el registro de un ingreso **rechazado**, se conserva el mensaje de rechazo para el usuario y el fallo queda en el log, sin datos personales (supuesto de implementación, confirmar).
  11. `POST`, `PUT`, `PATCH` y `DELETE` sobre `/api/bitacora` y `/api/bitacora/**` responden 405 para el administrador y 403 para los demás roles. `GlobalExceptionHandler` ahora responde 405 a un método no soportado y 400 («Parámetros de consulta inválidos») a un parámetro con tipo inválido; antes ambos respondían 500 en toda la API.
  12. El frontend de HU-058 se hace al final, en otra rama del repositorio del frontend.
* **Hallazgo (2026-11-06):** las migraciones no se pueden aplicar sobre una base vacía. La V11 falla con `relation "public.disponibilidades_docentes" does not exist`, probablemente porque la tabla la creaba la V9, que no está en el repositorio. Por eso la V29 se probó aislada, con una tabla mínima `periodos_academicos`. Pendiente con el equipo: recuperar V9 y V13.


## [2026-11-06] Exclusión de cursos y renumeración de grupos (HU-047)

* **HU afectadas:** HU-047 (revisar y ajustar la programación), HU-045 (generar), HU-046 (grupos), HU-058 (bitácora).
* **Decisión (confirmada por el equipo):**
  1. Un curso se excluye cuando no hay estudiantes de ese programa que vayan a ver esa asignatura en el periodo. Ejemplo: Cálculo la ven 3 programas (A en 1.º → grupo 50, B en 2.º → grupo 51, C en 2.º → grupo 52); C no tiene estudiantes, así que se excluye solo MAT101-52 y quedan 50 y 51.
  2. Se excluye un curso puntual (periodo + programa + asignatura + jornada) con justificación obligatoria. Queda en estado `EXCLUIDO`, visible en la consulta y sin número de grupo.
  3. Al excluir, los cursos activos restantes de esa asignatura en el periodo se renumeran consecutivos desde 50 conservando su orden relativo (si se excluye el 51, el antiguo 52 pasa a 51). Aplica a cualquier asignatura, común o no.
  4. Antes de confirmar, el sistema muestra cómo quedan los grupos (antes → después).
  5. Criterio 3: desde la pantalla «Cursos Programados» no existe forma de agregar cursos ni asignaturas fuera de la malla.
  6. Cada exclusión y cada renumeración se registran en la bitácora (HU-058), con valor anterior y nuevo, en la misma transacción.
  7. Al regenerar la programación, los cursos excluidos siguen excluidos con su justificación y la numeración respeta las exclusiones.
  8. **Series de grupos:** una sola serie por asignatura en el periodo, mezclando jornadas diurna y nocturna (como hoy).
  9. **Curso con docente asignado:** al intentar excluirlo se muestra una alerta emergente. Si el administrador confirma, se quita la asignación del docente; si cancela, no se realiza ningún cambio.
  10. **Deshacer una exclusión:** se permite reactivar el curso excluido.
  11. **Reordenamiento manual de grupos (C2):** queda fuera de esta HU. Solo hay renumeración automática.
  12. **Curso que sale de la oferta al regenerar** (cohorte desmarcada o asignatura retirada de la malla): no se borra (RN-13). Se marca «Requiere revisión» con el motivo y el administrador lo excluye con justificación.
  13. «Requiere revisión» (C4) se calcula al consultar, sin columna nueva y sin cambios en HU-032.
  14. Migración V30 (`V30__hu047_exclusion_cursos_programacion.sql`) aprobada para crear y probar en PostgreSQL 17 local. Aún no se aplica en la base compartida.
  15. **Curso reactivado:** toma el siguiente número de la serie (si quedan 50 y 51, pasa a 52); los demás cursos no cambian de grupo.
  16. Contrato `docs/contratos/HU-047.md` aprobado.
  17. La regeneración de la programación (HU-045) ya no borra cursos: concilia por programa, asignatura y jornada, conserva id, docente y exclusiones, y agrega los cursos nuevos al final de la serie de su asignatura.
* **Hallazgo (2026-11-06):** además de `disponibilidades_docentes` (V9), la V16 espera una tabla `usuarios` que ninguna migración del repositorio crea (probablemente la V13 aplicada solo en la base compartida). Con tablas sustitutas para ambas, la cadena V1–V30 se aplica completa en PostgreSQL 17 local.


## [2026-11-06] Cursos Programados muestra solo el número de grupo (presentación de HU-046 y HU-047)

* **HU afectadas:** HU-046 criterio 2 (presentación de los grupos) y HU-047 (submódulo 3 «Cursos Programados»).
* **Decisión (confirmada por el equipo):**
  1. La primera columna de la tabla se llama «Grupo» y muestra solo el número de grupo (50, 51, 52…), no el identificador `CÓDIGO-GRUPO`.
  2. Los cursos excluidos no tienen grupo: la celda muestra «—».
  3. En el modal de exclusión, la vista previa y los mensajes del submódulo, la asignatura se nombra por su nombre y los grupos como «Grupo 52 → Grupo 51».
  4. Es solo presentación: el curso se sigue identificando como `CÓDIGO-GRUPO` (asignatura + grupo); el backend, los DTO y el contrato `docs/contratos/HU-047.md` no cambian. Las llaves de React, las acciones y las llamadas al API usan el id del curso.
  5. El buscador sigue encontrando por código de asignatura y los filtros por programa, asignatura y jornada no cambian.
  6. Otras pantallas (exportación HU-014, vista del coordinador) no se modifican sin consultar. A la fecha, ninguna otra pantalla del frontend muestra el identificador compuesto.


## [2026-11-10] Ajustes de usabilidad en el registro de disponibilidad (HU-007 y HU-051)

* **HU afectadas:** HU-007 (registrar disponibilidad), HU-008 (editar disponibilidad), HU-051 (el administrador registra en nombre del docente).
* **Origen:** prueba de usabilidad con un docente. Requisitos confirmados por el equipo el 2026-11-10.
* **Decisión:**
  1. **R1. Registro mínimo de 1 hora.** La tabla conserva casillas de 30 minutos, pero cada intervalo continuo del mismo día dura mínimo 1 hora (se permiten 1 h, 1 h 30 min, 2 h…). Si el usuario deja un bloque de 30 minutos, se revierte el gesto y se muestra «No se puede seleccionar 30 minutos, el registro mínimo es 1 hora». El backend rechaza con el mismo mensaje (400) cualquier franja de menos de 60 minutos.
  2. **R2. Sábado sin disponibilidad de 19:00 a 21:00.** Las casillas se muestran bloqueadas («No habilitado»). El backend rechaza con «Hora fuera del rango permitido» (criterio 3.2 de HU-007) toda franja del sábado que termine después de las 19:00. La validación general de 07:00 a 21:00 se evalúa primero y conserva su mensaje.
  3. **R3. Botón «Seleccionar todo».** Marca todas las horas habilitadas (lunes a sábado de 07:00 a 21:00, excepto el sábado de 19:00 a 21:00) para luego guardar.
  4. **R4. Confirmación al guardar.** Se muestra «¿Desea guardar esta disponibilidad de horario?». Si se acepta, se guarda y aparece «Disponibilidad horaria registrada exitosamente». Si se cancela, se vuelve a la tabla sin perder lo marcado.
  5. Las cuatro reglas aplican igual al registro (POST), a la edición completa (PUT, HU-008) y a los flujos del administrador por `docenteId` (HU-051), porque todos usan `validarHorario`. Cuando la edición completa trae una franja inválida, se rechaza antes de borrar lo registrado.
  6. **Sin cambios en la base de datos.** El contrato de la API no cambia; solo se agregan dos respuestas 400 nuevas.
  7. Las franjas antiguas que violen las reglas (de 30 minutos, o del sábado después de las 19:00) **no se borran** (RN-13), pero se rechazan si se vuelven a enviar. En la interfaz, un dato antiguo de 30 minutos no bloquea otros cambios; se pide corregirlo al guardar. Al editar, las casillas bloqueadas del sábado se quitan de la selección.
* **Contradicción con el Excel (no se modifica el Excel ni el CSV):** HU-007 2.5 dice «¿Desea registrar este horario?» y 4.1 dice «Horario registrado». Se usan los textos nuevos de la prueba de usabilidad. Texto propuesto para el Excel:
  - **2.5.** Dado que el docente completa la información requerida, cuando presione «Guardar disponibilidad», entonces el sistema debe solicitar la confirmación con «¿Desea guardar esta disponibilidad de horario?». Si cancela, vuelve a la tabla de selección sin perder lo marcado.
  - **2.6. (nuevo)** El sistema debe ofrecer el botón «Seleccionar todo», que marca todas las horas habilitadas (lunes a sábado de 7:00 a. m. a 9:00 p. m., excepto el sábado de 7:00 p. m. a 9:00 p. m.).
  - **3.5. (nuevo)** Dado que el docente selecciona un intervalo continuo de menos de 1 hora, entonces el sistema debe revertir la selección y mostrar «No se puede seleccionar 30 minutos, el registro mínimo es 1 hora».
  - **3.6. (nuevo)** Dado que el docente intenta registrar disponibilidad el sábado de 7:00 p. m. a 9:00 p. m., entonces el sistema no debe permitirlo: las casillas aparecen como no habilitadas y, si se envía, se muestra «Hora fuera del rango permitido».
  - **4.1.** Dado que la disponibilidad fue registrada correctamente, cuando finalice la operación, entonces el sistema debe mostrar «Disponibilidad horaria registrada exitosamente» y el horario debe aparecer en el listado del docente.
* **Pendiente (no se implementa aquí):** el criterio 2 de HU-051 dice que el administrador registra aunque la ventana esté cerrada, indicando el motivo. Hoy el backend lo impide con `estaEnPlazo` en los flujos por `docenteId`.
