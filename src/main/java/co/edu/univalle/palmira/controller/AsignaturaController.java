package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.AsignaturaDto;
import co.edu.univalle.palmira.service.AsignaturaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/asignaturas")
@RequiredArgsConstructor
@Tag(name = "Asignaturas", description = "Endpoints para la gestión de asignaturas / materias")
public class AsignaturaController {

    private final AsignaturaService asignaturaService;

    @GetMapping
    @Operation(summary = "Listar todas las asignaturas")
    public ResponseEntity<List<AsignaturaDto.Response>> getAll() {
        return ResponseEntity.ok(asignaturaService.getAll());
    }

    @GetMapping("/{codeCourse}")
    @Operation(summary = "Obtener asignatura por código")
    public ResponseEntity<AsignaturaDto.Response> getByCode(@PathVariable("codeCourse") String codeCourse) {
        return ResponseEntity.ok(asignaturaService.getByCode(codeCourse));
    }

    @PostMapping
    @Operation(summary = "Crear una nueva asignatura")
    public ResponseEntity<AsignaturaDto.Response> create(@Valid @RequestBody AsignaturaDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(asignaturaService.create(request));
    }

    @PutMapping("/{codeCourse}")
    @Operation(summary = "Actualizar una asignatura existente")
    public ResponseEntity<AsignaturaDto.Response> update(
            @PathVariable("codeCourse") String codeCourse,
            @Valid @RequestBody AsignaturaDto.UpdateRequest request) {
        return ResponseEntity.ok(asignaturaService.update(codeCourse, request));
    }

    @PatchMapping("/{codeCourse}/estado")
    @Operation(summary = "Habilitar o deshabilitar una asignatura")
    public ResponseEntity<AsignaturaDto.Response> cambiarEstado(
            @PathVariable("codeCourse") String codeCourse,
            @RequestParam("activo") Boolean activo) {
        return ResponseEntity.ok(asignaturaService.cambiarEstado(codeCourse, activo));
    }

    @DeleteMapping("/{codeCourse}")
    @Operation(summary = "Eliminar una asignatura")
    public ResponseEntity<Void> delete(@PathVariable("codeCourse") String codeCourse) {
        asignaturaService.delete(codeCourse);
        return ResponseEntity.noContent().build();
    }
}
