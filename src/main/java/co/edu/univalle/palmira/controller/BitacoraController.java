package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.BitacoraDto;
import co.edu.univalle.palmira.service.BitacoraService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HU-058: consulta de la bitácora. Solo lectura (criterio 4): no hay endpoints para crear,
 * modificar ni borrar registros.
 */
@RestController
@RequestMapping("/api/bitacora")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
@Tag(name = "Bitácora", description = "HU-058: auditoría de cambios e ingresos (solo lectura)")
public class BitacoraController {

    private final BitacoraService bitacoraService;

    @GetMapping
    @Operation(summary = "Consultar la bitácora por usuario, entidad, periodo y rango de fechas")
    public ResponseEntity<BitacoraDto.Pagina> consultar(
            @RequestParam(value = "usuario", required = false) String usuario,
            @RequestParam(value = "entidad", required = false) String entidad,
            @RequestParam(value = "periodoId", required = false) Long periodoId,
            @RequestParam(value = "desde", required = false) String desde,
            @RequestParam(value = "hasta", required = false) String hasta,
            @RequestParam(value = "pagina", defaultValue = "0") int pagina,
            @RequestParam(value = "tamano", defaultValue = "20") int tamano) {
        return ResponseEntity.ok(bitacoraService.consultar(usuario, entidad, periodoId, desde, hasta, pagina, tamano));
    }

    /** Criterio 4: cualquier intento de crear, modificar o borrar responde 405. */
    @Hidden
    @RequestMapping(value = {"", "/**"},
            method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public void soloLectura(HttpServletRequest request) throws HttpRequestMethodNotSupportedException {
        throw new HttpRequestMethodNotSupportedException(request.getMethod(), List.of("GET"));
    }
}
