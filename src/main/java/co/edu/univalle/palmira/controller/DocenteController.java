package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.DocenteDto;
import co.edu.univalle.palmira.dto.EvaluacionDocenteDto;
import co.edu.univalle.palmira.service.DocenteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/docentes")
@RequiredArgsConstructor
public class DocenteController {

    private final DocenteService docenteService;

    // Listar todos los docentes para Plantilla
    @GetMapping
    public ResponseEntity<List<DocenteDto.Response>> listar() {
        return ResponseEntity.ok(docenteService.listarTodos());
    }

    @GetMapping("/evaluaciones")
    public ResponseEntity<List<EvaluacionDocenteDto>> listarEvaluaciones(
            @RequestParam("periodoId") Long periodoId,
            @RequestParam("codeCourse") String codeCourse) {
        return ResponseEntity.ok(
                docenteService.listarEvaluacionesAnteriores(periodoId, codeCourse));
    }

    @GetMapping("/{id}/evaluaciones")
    public ResponseEntity<List<EvaluacionDocenteDto.Curso>> listarCursosEvaluacion(
            @PathVariable("id") Long id, @RequestParam("periodoId") Long periodoId) {
        return ResponseEntity.ok(docenteService.listarCursosEvaluacion(id, periodoId));
    }

    @PutMapping("/{id}/evaluacion")
    public ResponseEntity<EvaluacionDocenteDto.Resumen> guardarEvaluacion(
            @PathVariable("id") Long id,
            @Valid @RequestBody EvaluacionDocenteDto.Request request) {
        return ResponseEntity.ok(docenteService.guardarEvaluacion(id, request));
    }

    // Activar / Inactivar docente
    @PatchMapping("/{id}/estado")
    public ResponseEntity<DocenteDto.Response> cambiarEstado(
            @PathVariable("id") Long id,
            @RequestParam("estado") Boolean estado) {
        return ResponseEntity.ok(docenteService.cambiarEstado(id, estado));
    }

    // crear perfil de docente
    @PostMapping
    public ResponseEntity<DocenteDto.Response> crear(@Valid @RequestBody DocenteDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(docenteService.crear(request));
    }

    // actualizar cuando el docente ya existe
    @PutMapping("/{id}")
    public ResponseEntity<DocenteDto.Response> actualizar(
            @PathVariable("id") Long id,
            @Valid @RequestBody DocenteDto.UpdateRequest request) {
        return ResponseEntity.ok(docenteService.actualizar(id, request));
    }

    // HU-022: Mi perfil. El docente sale del JWT: no se acepta correo por parámetro.
    @GetMapping("/me")
    public ResponseEntity<DocenteDto.Response> miPerfil(Authentication authentication) {
        return ResponseEntity.ok(docenteService.obtenerPorCorreo(correoAutenticado(authentication)));
    }

    // HU-015: editar mi correo personal, teléfono y facultad
    @PutMapping("/me/contacto")
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
