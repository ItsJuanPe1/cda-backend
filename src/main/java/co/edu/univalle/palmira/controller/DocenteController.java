package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.DocenteDto;
import co.edu.univalle.palmira.service.DocenteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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

    // HU-015: editar mi correo personal y teléfono
    @PutMapping("/me/contacto")
    public ResponseEntity<DocenteDto.Response> actualizarContacto(
            Authentication authentication,
            @Valid @RequestBody DocenteDto.ContactoRequest request) {
        return ResponseEntity.ok(
                docenteService.actualizarContacto(correoAutenticado(authentication), request));
    }

    // HU-015: subir o reemplazar mi foto (JPG o PNG, máximo 5 MB)
    @PutMapping(value = "/me/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocenteDto.Response> actualizarFoto(
            Authentication authentication,
            @RequestPart("foto") MultipartFile foto) {
        return ResponseEntity.ok(docenteService.actualizarFoto(correoAutenticado(authentication), foto));
    }

    // HU-015: obtener mi foto activa
    @GetMapping("/me/foto")
    public ResponseEntity<byte[]> obtenerFoto(Authentication authentication) {
        DocenteDto.Foto foto = docenteService.obtenerFoto(correoAutenticado(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.tipoMime()))
                .cacheControl(CacheControl.noStore())
                .body(foto.contenido());
    }

    // HU-015: eliminar (desactivar) mi foto
    @DeleteMapping("/me/foto")
    public ResponseEntity<Void> eliminarFoto(Authentication authentication) {
        docenteService.eliminarFoto(correoAutenticado(authentication));
        return ResponseEntity.noContent().build();
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