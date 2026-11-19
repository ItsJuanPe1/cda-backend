package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.DocenteDto;
import co.edu.univalle.palmira.dto.EvaluacionDocenteDto;
import co.edu.univalle.palmira.service.DocenteService;
import co.edu.univalle.palmira.service.EvaluacionDocenteExcelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Controlador REST para la administración y consulta de docentes.
 * <p>
 * Incluye operaciones para el listado de docentes, gestión de estado
 * (activar/desactivar),
 * creación y actualización de perfiles, consulta de mi perfil (/me),
 * actualización de datos
 * de contacto y registro/consulta de evaluaciones docentes.
 * </p>
 */
@RestController
@RequestMapping("/api/docentes")
@Tag(name = "Docentes", description = "Endpoints para la administracion y consulta de informacion docente")
public class DocenteController {

    private final DocenteService docenteService;
    private final EvaluacionDocenteExcelService evaluacionDocenteExcelService;

    @Autowired
    public DocenteController(
            DocenteService docenteService,
            EvaluacionDocenteExcelService evaluacionDocenteExcelService) {
        this.docenteService = docenteService;
        this.evaluacionDocenteExcelService = evaluacionDocenteExcelService;
    }

    /**
     * Lista todos los docentes registrados para la vista de plantilla general.
     *
     * @return lista de docentes con sus datos institucionales y de vinculación
     */
    @GetMapping
    @Operation(summary = "Listar todos los docentes registrados")
    public ResponseEntity<List<DocenteDto.Response>> listar() {
        return ResponseEntity.ok(docenteService.listarTodos());
    }

    /**
     * Consulta las evaluaciones históricas de docentes para una asignatura y
     * período específicos.
     *
     * @param periodoId  identificador del período
     * @param codeCourse código de la asignatura
     * @return lista de evaluaciones históricas registradas
     */
    @GetMapping("/evaluaciones")
    @Operation(summary = "Listar evaluaciones historicas de docentes para una asignatura")
    public ResponseEntity<List<EvaluacionDocenteDto>> listarEvaluaciones(
            @RequestParam("periodoId") Long periodoId,
            @RequestParam("codeCourse") String codeCourse) {
        return ResponseEntity.ok(
                docenteService.listarEvaluacionesAnteriores(periodoId, codeCourse));
    }

    /**
     * Importa masivamente las calificaciones docentes desde un libro Excel (.xlsx / .xls).
     * Promedia múltiples grupos de la misma asignatura por docente y marca vacíos o 0 como "No evaluado".
     *
     * @param periodoId identificador del período activo
     * @param file      archivo Excel con las calificaciones
     * @return resumen detallado de los registros procesados
     */
    @PostMapping(value = "/evaluaciones/importar-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_COORDINADOR', 'COORDINADOR')")
    @Operation(summary = "Importar calificaciones docentes desde archivo Excel")
    public ResponseEntity<EvaluacionDocenteDto.ImportacionExcelResponse> importarEvaluacionesExcel(
            @RequestParam("periodoId") Long periodoId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(evaluacionDocenteExcelService.importar(periodoId, file));
    }

    /**
     * Lista los cursos que un docente dictó en el período anterior para registrar
     * su evaluación.
     *
     * @param id        identificador del docente
     * @param periodoId identificador del período actual
     * @return lista de cursos evaluables
     */
    @GetMapping("/{id}/evaluaciones")
    @Operation(summary = "Listar cursos dictados en periodo anterior para evaluar")
    public ResponseEntity<List<EvaluacionDocenteDto.Curso>> listarCursosEvaluacion(
            @PathVariable("id") Long id, @RequestParam("periodoId") Long periodoId) {
        return ResponseEntity.ok(docenteService.listarCursosEvaluacion(id, periodoId));
    }

    /**
     * Registra o actualiza la calificación de evaluación docente de un curso
     * anterior.
     *
     * @param id      identificador del docente
     * @param request datos de la evaluación (código de curso, período,
     *                calificación)
     * @return resumen de la evaluación guardada
     */
    @PutMapping("/{id}/evaluacion")
    @Operation(summary = "Guardar calificacion de evaluacion docente")
    public ResponseEntity<EvaluacionDocenteDto.Resumen> guardarEvaluacion(
            @PathVariable("id") Long id,
            @Valid @RequestBody EvaluacionDocenteDto.Request request) {
        return ResponseEntity.ok(docenteService.guardarEvaluacion(id, request));
    }

    /**
     * Activa o desactiva a un docente en el sistema.
     *
     * @param id     identificador del docente
     * @param estado nuevo estado activo (true/false)
     * @return información actualizada del docente
     */
    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar o desactivar docente")
    public ResponseEntity<DocenteDto.Response> cambiarEstado(
            @PathVariable("id") Long id,
            @RequestParam("estado") Boolean estado) {
        return ResponseEntity.ok(docenteService.cambiarEstado(id, estado));
    }

    /**
     * Crea un nuevo docente en el sistema.
     *
     * @param request datos del docente a registrar
     * @return docente registrado con código HTTP 201 Created
     */
    @PostMapping
    @Operation(summary = "Crear nuevo docente")
    public ResponseEntity<DocenteDto.Response> crear(@Valid @RequestBody DocenteDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(docenteService.crear(request));
    }

    /**
     * Actualiza la información de un docente existente.
     *
     * @param id      identificador del docente
     * @param request datos a modificar
     * @return docente actualizado
     */
    @PutMapping("/{id}")
    @Operation(summary = "Actualizar docente existente")
    public ResponseEntity<DocenteDto.Response> actualizar(
            @PathVariable("id") Long id,
            @Valid @RequestBody DocenteDto.UpdateRequest request) {
        return ResponseEntity.ok(docenteService.actualizar(id, request));
    }

    /**
     * Consulta el perfil del docente autenticado según los datos del token JWT.
     *
     * @param authentication objeto de autenticación de Spring Security
     * @return perfil del docente
     */
    @GetMapping("/me")
    @Operation(summary = "Consultar perfil del docente autenticado")
    public ResponseEntity<DocenteDto.Response> miPerfil(Authentication authentication) {
        return ResponseEntity.ok(docenteService.obtenerPorCorreo(correoAutenticado(authentication)));
    }

    /**
     * Actualiza los datos de contacto personal (teléfono, correo personal) del
     * docente autenticado.
     *
     * @param authentication objeto de autenticación
     * @param request        nuevos datos de contacto
     * @return datos actualizados
     */
    @PutMapping("/me/contacto")
    @Operation(summary = "Actualizar datos de contacto del docente autenticado")
    public ResponseEntity<DocenteDto.Response> actualizarContacto(
            Authentication authentication,
            @Valid @RequestBody DocenteDto.ContactoRequest request) {
        return ResponseEntity.ok(
                docenteService.actualizarContacto(correoAutenticado(authentication), request));
    }

    private static String correoAutenticado(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida o expirada");
        }
        if (authentication.getPrincipal() instanceof Jwt jwt && jwt.getClaimAsString("email") != null) {
            return jwt.getClaimAsString("email");
        }
        return authentication.getName();
    }
}
