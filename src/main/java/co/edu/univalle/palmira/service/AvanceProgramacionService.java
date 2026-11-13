package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.AvanceProgramacionDto;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.repository.CursoProgramacionRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AvanceProgramacionService {

    public static final String MSG_PERIODO_NO_ENCONTRADO =
            "Período académico no encontrado con ID: ";

    private final PeriodoAcademicoRepository periodoRepository;
    private final CursoProgramacionRepository cursoRepository;
    private final DocenteRepository docenteRepository;
    private final AvanceProgramacionExcelService excelService;

    @Transactional(readOnly = true)
    public AvanceProgramacionDto.Resumen obtenerAvance(Long periodoId) {
        PeriodoAcademico periodo = periodoRepository.findById(periodoId)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_PERIODO_NO_ENCONTRADO + periodoId));

        List<AvanceProgramacionDto.Programa> programas = cursoRepository.contarAvancePorPrograma(periodoId)
                .stream()
                .map(this::toPrograma)
                .toList();

        List<AvanceProgramacionDto.DocenteSinDisponibilidad> docentesSinDisponibilidad =
                docenteRepository.findActivosSinDisponibilidad(periodoId)
                        .stream()
                        .map(this::toDocenteSinDisponibilidad)
                        .toList();

        long generados = programas.stream().mapToLong(AvanceProgramacionDto.Programa::generados).sum();
        long conDocente = programas.stream().mapToLong(AvanceProgramacionDto.Programa::conDocente).sum();
        long pendientesAsignacion = programas.stream()
                .mapToLong(AvanceProgramacionDto.Programa::pendientesAsignacion).sum();
        long pendientesContratacion = programas.stream()
                .mapToLong(AvanceProgramacionDto.Programa::pendientesContratacion).sum();
        long excluidos = programas.stream().mapToLong(AvanceProgramacionDto.Programa::excluidos).sum();

        Instant ahora = Instant.now();
        AvanceProgramacionDto.Ventanas ventanas = new AvanceProgramacionDto.Ventanas(
                periodo.getPlazoDocenteInicio(),
                periodo.getPlazoDocenteFin(),
                periodo.getPlazoCoordinadorInicio(),
                periodo.getPlazoCoordinadorFin(),
                abierta(periodo.getPlazoDocenteInicio(), periodo.getPlazoDocenteFin(), ahora),
                abierta(periodo.getPlazoCoordinadorInicio(), periodo.getPlazoCoordinadorFin(), ahora));

        return new AvanceProgramacionDto.Resumen(
                new AvanceProgramacionDto.Periodo(
                        periodo.getId(), periodo.getNombre(), periodo.getEstado(), ventanas),
                new AvanceProgramacionDto.Totales(
                        generados, conDocente, pendientesAsignacion, pendientesContratacion,
                        excluidos, docentesSinDisponibilidad.size()),
                programas,
                docentesSinDisponibilidad,
                ahora);
    }

    @Transactional(readOnly = true)
    public byte[] generarExcel(Long periodoId) {
        AvanceProgramacionDto.Resumen avance = obtenerAvance(periodoId);
        List<AvanceProgramacionDto.CursoPendiente> pendientes = cursoRepository
                .findPendientesParaExcel(periodoId)
                .stream()
                .map(this::toCursoPendiente)
                .toList();
        return excelService.generar(avance, pendientes);
    }

    private AvanceProgramacionDto.Programa toPrograma(Object[] row) {
        return new AvanceProgramacionDto.Programa(
                (String) row[0],
                (String) row[1],
                numero(row[2]),
                numero(row[3]),
                numero(row[4]),
                numero(row[5]),
                numero(row[6]));
    }

    private AvanceProgramacionDto.DocenteSinDisponibilidad toDocenteSinDisponibilidad(Docente docente) {
        String facultad = docente.getFacultad() != null ? docente.getFacultad().getNombre() : null;
        String programa = docente.getPrograma() != null ? docente.getPrograma().getNameProgram() : null;
        return new AvanceProgramacionDto.DocenteSinDisponibilidad(
                docente.getId(),
                docente.getCodigo(),
                (docente.getNombres() + " " + docente.getApellidos()).trim(),
                docente.getCorreoInstitucional(),
                facultad,
                programa);
    }

    private AvanceProgramacionDto.CursoPendiente toCursoPendiente(Object[] row) {
        return new AvanceProgramacionDto.CursoPendiente(
                (String) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                row[4] != null ? ((Number) row[4]).intValue() : null,
                row[5] != null ? String.valueOf(row[5]) : null,
                row[6] != null ? ((Number) row[6]).intValue() : null,
                (String) row[7],
                (String) row[8]);
    }

    private static boolean abierta(Instant inicio, Instant fin, Instant ahora) {
        return inicio != null && fin != null
                && !ahora.isBefore(inicio)
                && !ahora.isAfter(fin);
    }

    private static long numero(Object valor) {
        return valor != null ? ((Number) valor).longValue() : 0L;
    }
}
