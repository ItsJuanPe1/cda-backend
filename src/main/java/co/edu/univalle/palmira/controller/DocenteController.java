package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.DocenteDto;
import co.edu.univalle.palmira.service.DocenteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @PathVariable Long id,
            @RequestParam("estado") Boolean estado) {
        return ResponseEntity.ok(docenteService.cambiarEstado(id, estado));
    }

    // Listado de docentes (para la vista con buscador y filtros)
    @GetMapping
    public ResponseEntity<List<DocenteDto.Response>> listar() {
        return ResponseEntity.ok(docenteService.listar());
    }

    // crear perfil de docente
    @PostMapping
    public ResponseEntity<DocenteDto.Response> crear(@Valid @RequestBody DocenteDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(docenteService.crear(request));
    }

    // actualizar cuando el docente ya existe
    @PutMapping("/{id}")
    public ResponseEntity<DocenteDto.Response> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody DocenteDto.UpdateRequest request) {
        return ResponseEntity.ok(docenteService.actualizar(id, request));
    }

    // HU-022: Mi perfil
    @GetMapping("/me")
    public ResponseEntity<DocenteDto.Response> miPerfil(@RequestParam("correo") String correo) {
        return ResponseEntity.ok(docenteService.obtenerPorCorreo(correo));
    }
}