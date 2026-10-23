package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.FacultadDto;
import co.edu.univalle.palmira.service.FacultadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/facultades")
@RequiredArgsConstructor
@Tag(name = "Facultades", description = "Endpoints para la gestión de facultades universitarias")
public class FacultadController {

    private final FacultadService facultadService;

    @GetMapping
    @Operation(summary = "Listar todas las facultades")
    public ResponseEntity<List<FacultadDto.Response>> getAll() {
        return ResponseEntity.ok(facultadService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener facultad por ID")
    public ResponseEntity<FacultadDto.Response> getById(@PathVariable("id") Integer id) {
        return ResponseEntity.ok(facultadService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Crear una nueva facultad")
    public ResponseEntity<FacultadDto.Response> create(@Valid @RequestBody FacultadDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(facultadService.create(request));
    }
}
