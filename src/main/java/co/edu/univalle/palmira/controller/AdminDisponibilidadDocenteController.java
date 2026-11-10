package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.DisponibilidadDocenteDto;
import co.edu.univalle.palmira.service.DisponibilidadDocenteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Operaciones administrativas sobre la disponibilidad de un docente seleccionado.
 * El ID de la ruta siempre corresponde a docentes.id, no a usuarios.id.
 */
@RestController
@RequestMapping("/api/admin/docentes/{docenteId}/disponibilidades")
@RequiredArgsConstructor
public class AdminDisponibilidadDocenteController {

    private final DisponibilidadDocenteService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_COORDINADOR', 'COORDINADOR')")
    public DisponibilidadDocenteDto.Response consultar(@PathVariable("docenteId") Long docenteId) {
        return service.consultarPorDocenteId(docenteId);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    public DisponibilidadDocenteDto.Item registrar(
            @PathVariable("docenteId") Long docenteId,
            @Valid @RequestBody DisponibilidadDocenteDto.Request request) {
        return service.registrarPorDocenteId(docenteId, request);
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    public List<DisponibilidadDocenteDto.Item> actualizarTodo(
            @PathVariable("docenteId") Long docenteId,
            @Valid @RequestBody List<DisponibilidadDocenteDto.Request> requests) {
        return service.actualizarTodoPorDocenteId(docenteId, requests);
    }
}
