package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.DocenteDto;
import co.edu.univalle.palmira.service.DocenteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/docentes")
@RequiredArgsConstructor
public class DocenteController {

    private final DocenteService docenteService;

    // HU-026: crear perfil de docente
    @PostMapping
    public ResponseEntity<DocenteDto.Response> crear(@Valid @RequestBody DocenteDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(docenteService.crear(request));
    }

    // HU-026 punto 6: actualizar cuando el docente ya existe
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