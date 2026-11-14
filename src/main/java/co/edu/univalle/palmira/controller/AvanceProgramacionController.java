package co.edu.univalle.palmira.controller;

import co.edu.univalle.palmira.dto.AvanceProgramacionDto;
import co.edu.univalle.palmira.service.AvanceProgramacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/programacion/avance")
@RequiredArgsConstructor
@Tag(name = "Avance de programación", description = "Tablero de avance y exportación de pendientes del período")
public class AvanceProgramacionController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AvanceProgramacionService avanceService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @Operation(summary = "Consultar el avance de la programación de un período")
    public ResponseEntity<AvanceProgramacionDto.Resumen> obtenerAvance(
            @RequestParam("periodoId") Long periodoId) {
        return ResponseEntity.ok(avanceService.obtenerAvance(periodoId));
    }

    @GetMapping("/excel")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @Operation(summary = "Descargar en Excel los cursos pendientes y docentes sin disponibilidad")
    public ResponseEntity<byte[]> descargarExcel(@RequestParam("periodoId") Long periodoId) {
        byte[] archivo = avanceService.generarExcel(periodoId);
        String marca = ZonedDateTime.now(ZoneId.of("America/Bogota"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        String nombre = "avance-programacion-" + periodoId + "-" + marca + ".xlsx";
        String disposition = ContentDisposition.attachment()
                .filename(nombre, StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentLength(archivo.length)
                .body(archivo);
    }
}
