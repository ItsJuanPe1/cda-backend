package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.DocenteAsignaturaDto;
import co.edu.univalle.palmira.service.DocenteAsignaturaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/docentes-asignaturas")
@RequiredArgsConstructor
@Tag(name = "Docentes - Asignaturas", description = "HU-049: histórico de asignaturas dictadas por docente")
public class DocenteAsignaturaController {

    private final DocenteAsignaturaService service;

    @GetMapping
    @Operation(summary = "Consultar asociaciones por docente, asignatura, facultad o estado")
    public ResponseEntity<List<DocenteAsignaturaDto.Response>> listar(
            @RequestParam(value = "docenteId", required = false) Long docenteId,
            @RequestParam(value = "codeCourse", required = false) String codeCourse,
            @RequestParam(value = "idFacultad", required = false) Integer idFacultad,
            @RequestParam(value = "activo", required = false) Boolean activo) {
        return ResponseEntity.ok(service.listar(docenteId, codeCourse, idFacultad, activo));
    }

    @PostMapping
    @Operation(summary = "Asociar un docente a una asignatura de su facultad")
    public ResponseEntity<DocenteAsignaturaDto.Response> asociar(
            @Valid @RequestBody DocenteAsignaturaDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.asociar(request));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar o desactivar una asociación")
    public ResponseEntity<DocenteAsignaturaDto.Response> cambiarEstado(
            @PathVariable("id") Long id,
            @RequestParam("activo") Boolean activo) {
        return ResponseEntity.ok(service.cambiarEstado(id, Boolean.TRUE.equals(activo)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Quitar una asociación (la desactiva, no la elimina)")
    public ResponseEntity<Void> quitar(@PathVariable("id") Long id) {
        service.quitar(id);
        return ResponseEntity.noContent().build();
    }
}
