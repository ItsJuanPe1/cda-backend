package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.PeriodoAcademicoDto;
import co.edu.univalle.palmira.service.PeriodoAcademicoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/periodos")
@RequiredArgsConstructor
public class PeriodoAcademicoController {
    private final PeriodoAcademicoService service;

    @GetMapping
    public List<PeriodoAcademicoDto.Response> listar() { return service.listar(); }

    @GetMapping("/vigente")
    public ResponseEntity<PeriodoAcademicoDto.Response> vigente() {
        PeriodoAcademicoDto.Response response = service.vigente();
        return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public PeriodoAcademicoDto.Response obtenerPorId(@PathVariable("id") Long id) {
        return service.obtenerPorId(id);
    }

    @PostMapping
    public ResponseEntity<PeriodoAcademicoDto.Response> crear(@Valid @RequestBody PeriodoAcademicoDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @PutMapping("/{id}")
    public PeriodoAcademicoDto.Response actualizar(@PathVariable("id") Long id,
            @Valid @RequestBody PeriodoAcademicoDto.Request request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable("id") Long id) {
        service.desactivar(id);
    }
}
