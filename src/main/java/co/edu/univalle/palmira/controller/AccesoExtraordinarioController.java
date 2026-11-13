package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.AccesoExtraordinarioDto;
import co.edu.univalle.palmira.service.AccesoExtraordinarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo administrador: la ruta cae bajo la regla /api/admin/** de SecurityConfig. */
@RestController
@RequestMapping("/api/admin/docentes/{docenteId}/acceso-extraordinario")
@RequiredArgsConstructor
@Tag(name = "Acceso extraordinario", description = "Plazo extraordinario para que un docente edite su disponibilidad fuera de la ventana general")
public class AccesoExtraordinarioController {

    private final AccesoExtraordinarioService accesoService;

    @PostMapping
    @Operation(summary = "Habilita un acceso extraordinario al docente (fecha de fin y motivo obligatorios)")
    public ResponseEntity<AccesoExtraordinarioDto.Respuesta> otorgar(
            @PathVariable("docenteId") Long docenteId,
            @Valid @RequestBody AccesoExtraordinarioDto.Request request,
            @AuthenticationPrincipal Jwt jwt) {
        AccesoExtraordinarioDto.Respuesta respuesta =
                accesoService.otorgar(docenteId, correoDe(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @GetMapping
    @Operation(summary = "Acceso extraordinario vigente del docente; 204 si no tiene")
    public ResponseEntity<AccesoExtraordinarioDto.Respuesta> consultar(@PathVariable("docenteId") Long docenteId) {
        return accesoService.consultar(docenteId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping
    @Operation(summary = "Revoca el acceso extraordinario vigente del docente")
    public ResponseEntity<Void> revocar(@PathVariable("docenteId") Long docenteId,
                                        @AuthenticationPrincipal Jwt jwt) {
        accesoService.revocar(docenteId, correoDe(jwt));
        return ResponseEntity.noContent().build();
    }

    private static String correoDe(Jwt jwt) {
        return jwt != null ? jwt.getClaimAsString("email") : null;
    }
}