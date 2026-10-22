package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.CoordinadorDto;
import co.edu.univalle.palmira.service.CoordinadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coordinadores")
@RequiredArgsConstructor
@Tag(name = "Coordinadores", description = "Endpoints para la gestión de coordinadores de programas académicos")
public class CoordinadorController {

    private final CoordinadorService coordinadorService;

    @GetMapping
    @Operation(summary = "Listar todos los coordinadores con su programa vinculado")
    public ResponseEntity<List<CoordinadorDto.Response>> getAll() {
        return ResponseEntity.ok(coordinadorService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un coordinador por ID")
    public ResponseEntity<CoordinadorDto.Response> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(coordinadorService.getById(id));
    }

    @GetMapping("/programa/{codeProgram}")
    @Operation(summary = "Listar coordinadores de un programa académico específico")
    public ResponseEntity<List<CoordinadorDto.Response>> getByPrograma(@PathVariable String codeProgram) {
        return ResponseEntity.ok(coordinadorService.getByPrograma(codeProgram));
    }

    @PostMapping
    @Operation(summary = "Crear y registrar un nuevo coordinador")
    public ResponseEntity<CoordinadorDto.Response> create(@Valid @RequestBody CoordinadorDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(coordinadorService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar información de un coordinador existente")
    public ResponseEntity<CoordinadorDto.Response> update(
            @PathVariable Integer id,
            @Valid @RequestBody CoordinadorDto.UpdateRequest request) {
        return ResponseEntity.ok(coordinadorService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un coordinador")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        coordinadorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
