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
