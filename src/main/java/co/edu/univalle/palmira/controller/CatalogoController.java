package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.CatalogoItemDto;
import co.edu.univalle.palmira.service.CoordinadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Catálogos", description = "Endpoints para la consulta de catálogos del sistema (categorías, tipos de contrato)")
public class CatalogoController {

    private final CoordinadorService coordinadorService;

    @GetMapping("/categorias")
    @Operation(summary = "Listar todas las categorías académicas (escalafón docente)")
    public ResponseEntity<List<CatalogoItemDto>> getCategorias() {
        return ResponseEntity.ok(coordinadorService.getCategorias());
    }

    @GetMapping("/tipos-contrato")
    @Operation(summary = "Listar todos los tipos de contrato laboral")
    public ResponseEntity<List<CatalogoItemDto>> getTiposContrato() {
        return ResponseEntity.ok(coordinadorService.getTiposContrato());
    }
}
