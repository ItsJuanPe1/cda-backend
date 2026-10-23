package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.PeriodoDto;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Periodo;
import co.edu.univalle.palmira.repository.PeriodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeriodoService {

    private final PeriodoRepository periodoRepository;

    public List<PeriodoDto.Resumen> getAll() {
        return periodoRepository.findAllByOrderByInicioSemestreDesc().stream()
                .map(this::toResumen)
                .toList();
    }

    public PeriodoDto.Detalle getById(Long id) {
        Periodo p = periodoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el período con id " + id));
        return toDetalle(p);
    }

    private PeriodoDto.Resumen toResumen(Periodo p) {
        return new PeriodoDto.Resumen(p.getId(), p.getNombre(), p.getEstado(),
                p.getInicioSemestre(), p.getFinSemestre(), p.getFechaDesactivacion());
    }

    private PeriodoDto.Detalle toDetalle(Periodo p) {
        // El backend solo entrega fechas: NO calcula Abierto/Cerrado (lo hace el front).
        PeriodoDto.Plazos plazos = new PeriodoDto.Plazos(
                plazo(p.getPlazoDocentesInicio(), p.getPlazoDocentesFin()),
                plazo(p.getPlazoCoordinadoresInicio(), p.getPlazoCoordinadoresFin()));
        return new PeriodoDto.Detalle(p.getId(), p.getNombre(), p.getEstado(),
                p.getNumeroResolucion(), p.getEnlaceResolucion(),
                p.getInicioSemestre(), p.getFinSemestre(),
                p.getFechaAdicion(), p.getFechaCancelacion(),
                plazos, p.getFechaDesactivacion());
    }

    private PeriodoDto.Plazo plazo(OffsetDateTime inicio, OffsetDateTime fin) {
        return (inicio == null && fin == null) ? null : new PeriodoDto.Plazo(inicio, fin);
    }
}