package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.ProgramacionDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.*;
import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgramacionService {

    private final CursoProgramacionRepository cursoRepository;
    private final CohortePeriodoRepository cohorteRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final ProgramaRepository programaRepository;
    private final PensumAcademicoRepository pensumRepository;
    private final DocenteRepository docenteRepository;
    private final DocenteAsignaturaService docenteAsignaturaService;

    @Transactional
    public ProgramacionDto.ResumenResponse generarProgramacion(Long periodoId, boolean sobrescribir) {
        PeriodoAcademico periodo = periodoRepository.findById(periodoId)
                .orElseThrow(() -> new ResourceNotFoundException("Período académico no encontrado con ID: " + periodoId));

        long conteoExistente = cursoRepository.countByPeriodoId(periodoId);
        if (conteoExistente > 0 && !sobrescribir) {
            throw new ConflictException("Ya existe una programación generada para este período con " + conteoExistente + " cursos. Requiere confirmación para regenerar.");
        }

        // 1. Validar que todos los programas activos tengan cohortes confirmadas
        List<Programa> programasActivos = programaRepository.findByEstadoOrderByNameProgramAsc(EstadoPrograma.ACTIVO);
        List<CohortePeriodo> cohortesConfirmadas = cohorteRepository.findByPeriodoIdAndConfirmadaTrue(periodoId);

        Set<String> programasConCohortes = cohortesConfirmadas.stream()
                .map(c -> c.getPrograma().getCodeProgram())
                .collect(Collectors.toSet());

        List<String> programasFaltantes = programasActivos.stream()
                .filter(p -> !programasConCohortes.contains(p.getCodeProgram()))
                .map(Programa::getNameProgram)
                .collect(Collectors.toList());

        if (!programasFaltantes.isEmpty()) {
            throw new BadRequestException("No se puede generar la programación: faltan cohortes confirmadas para los programas: " +
                    String.join(", ", programasFaltantes));
        }

        // 2. Validar que cada cohorte tenga asignaturas en su malla correspondiente y recolectar las materias
        Map<String, Long> respaldoDocentes = new HashMap<>();
        if (sobrescribir && conteoExistente > 0) {
            List<CursoProgramacion> cursosActuales = cursoRepository.findByPeriodoIdWithDetails(periodoId);
            for (CursoProgramacion c : cursosActuales) {
                if (c.getDocente() != null) {
                    String clave = c.getPrograma().getCodeProgram() + "#" + c.getAsignatura().getCodeCourse() + "#" + c.getJornada();
                    respaldoDocentes.put(clave, c.getDocente().getId());
                }
            }
            cursoRepository.deleteByPeriodoId(periodoId);
            cursoRepository.flush();
        }

        List<CursoProgramacion> nuevosCursos = new ArrayList<>();
        Set<String> cursosUnicos = new HashSet<>();

        for (CohortePeriodo cohorte : cohortesConfirmadas) {
            Programa prog = cohorte.getPrograma();
            List<PensumAcademico> materiasMalla = pensumRepository.findActiveByCodeProgramAndResolucionAndSemestre(
                    prog.getCodeProgram(), cohorte.getNumeroResolucion(), cohorte.getSemestre());

            // Las mallas creadas antes de V20 no tenían número de resolución.
            // En ese caso, la cohorte conserva la resolución seleccionada, pero
            // las asignaturas deben localizarse por programa y semestre.
            if (materiasMalla.isEmpty()) {
                materiasMalla = pensumRepository.findActiveByCodeProgramAndSemestre(
                        prog.getCodeProgram(), cohorte.getSemestre());
            }

            // Excluir electivas profesionales
            List<PensumAcademico> obligatorias = materiasMalla.stream()
                    .filter(p -> p.getIsElective() == null || !p.getIsElective())
                    .collect(Collectors.toList());

            if (obligatorias.isEmpty()) {
                throw new BadRequestException("La cohorte de semestre " + cohorte.getSemestre() + " del programa " +
                        prog.getNameProgram() + " con resolución '" + cohorte.getNumeroResolucion() + "' no tiene asignaturas obligatorias en la malla.");
            }

            for (PensumAcademico pensum : obligatorias) {
                Asignatura asig = pensum.getAsignatura();
                String claveUnica = prog.getCodeProgram() + "#" + asig.getCodeCourse() + "#" + cohorte.getJornada();

                if (!cursosUnicos.add(claveUnica)) {
                    continue; // Evitar duplicar materia en la misma jornada para el mismo programa
                }

                String nombreSnapshot = pensum.getNameCourseSnapshot() != null && !pensum.getNameCourseSnapshot().isBlank()
                        ? pensum.getNameCourseSnapshot()
                        : asig.getNameCourse();

                Docente docenteAsignado = null;
                String estado = "PROGRAMADO";
                if (respaldoDocentes.containsKey(claveUnica)) {
                    Long idDocenteAnterior = respaldoDocentes.get(claveUnica);
                    docenteAsignado = docenteRepository.findById(idDocenteAnterior).orElse(null);
                    if (docenteAsignado != null) {
                        estado = "ASIGNADO";
                    }
                }

                nuevosCursos.add(CursoProgramacion.builder()
                        .periodo(periodo)
                        .programa(prog)
                        .asignatura(asig)
                        .semestre(cohorte.getSemestre())
                        .jornada(cohorte.getJornada())
                        .numeroResolucion(cohorte.getNumeroResolucion())
                        .credits(asig.getCredits() != null ? asig.getCredits() : 0)
                        .nombreAsignatura(nombreSnapshot)
                        .docente(docenteAsignado)
                        .estado(estado)
                        .build());
            }
        }

        // 3. Asignación secuencial de grupos (HU-046):
        // Para cada asignatura compartida, asignar grupos consecutivos desde 50 (50, 51, 52...)
        Map<String, List<CursoProgramacion>> cursosPorAsignatura = nuevosCursos.stream()
                .collect(Collectors.groupingBy(c -> c.getAsignatura().getCodeCourse()));

        for (Map.Entry<String, List<CursoProgramacion>> entry : cursosPorAsignatura.entrySet()) {
            List<CursoProgramacion> cursosDeMateria = entry.getValue();
            // Ordenar por programa y jornada para estabilidad
            cursosDeMateria.sort(Comparator.comparing((CursoProgramacion c) -> c.getPrograma().getCodeProgram())
                    .thenComparing(CursoProgramacion::getJornada));

            int grupo = 50;
            for (CursoProgramacion curso : cursosDeMateria) {
                curso.setGrupo(grupo++);
            }
        }

        cursoRepository.saveAll(nuevosCursos);

        // HU-049 criterio 4: lo que el docente dicta en el periodo pasa al histórico.
        docenteAsignaturaService.registrarDesdeCursos(nuevosCursos);

        return construirResumen(periodoId, "Programación generada exitosamente con " + nuevosCursos.size() + " cursos.");
    }

    @Transactional(readOnly = true)
    public List<ProgramacionDto.CursoResponse> obtenerProgramacion(Long periodoId, String codeProgram, String codeCourse, Jornada jornada) {
        periodoRepository.findById(periodoId)
                .orElseThrow(() -> new ResourceNotFoundException("Período académico no encontrado con ID: " + periodoId));

        List<CursoProgramacion> lista = (codeProgram != null && !codeProgram.isBlank())
                ? cursoRepository.findByPeriodoIdAndCodeProgramWithDetails(periodoId, codeProgram)
                : cursoRepository.findByPeriodoIdWithDetails(periodoId);

        return lista.stream()
                .filter(c -> codeCourse == null || codeCourse.isBlank() || c.getAsignatura().getCodeCourse().equalsIgnoreCase(codeCourse))
                .filter(c -> jornada == null || c.getJornada() == jornada)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProgramacionDto.ResumenResponse obtenerResumen(Long periodoId) {
        periodoRepository.findById(periodoId)
                .orElseThrow(() -> new ResourceNotFoundException("Período académico no encontrado con ID: " + periodoId));
        return construirResumen(periodoId, "Resumen consolidado de la programación");
    }

    private ProgramacionDto.ResumenResponse construirResumen(Long periodoId, String mensaje) {
        List<Object[]> conteos = cursoRepository.contarCursosPorPrograma(periodoId);
        List<ProgramacionDto.ResumenProgramaItem> items = new ArrayList<>();
        long total = 0;

        for (Object[] row : conteos) {
            String code = (String) row[0];
            String nombre = (String) row[1];
            long cantidad = ((Number) row[2]).longValue();
            total += cantidad;

            items.add(ProgramacionDto.ResumenProgramaItem.builder()
                    .codeProgram(code)
                    .nombrePrograma(nombre)
                    .totalCursos(cantidad)
                    .build());
        }

        return ProgramacionDto.ResumenResponse.builder()
                .totalCursos(total)
                .programas(items)
                .mensaje(mensaje)
                .build();
    }

    private ProgramacionDto.CursoResponse toResponse(CursoProgramacion c) {
        String nombreDocente = null;
        Long idDocente = null;
        if (c.getDocente() != null) {
            idDocente = c.getDocente().getId();
            nombreDocente = c.getDocente().getNombres() + " " + c.getDocente().getApellidos();
        }

        return ProgramacionDto.CursoResponse.builder()
                .id(c.getId())
                .periodoId(c.getPeriodo().getId())
                .codeProgram(c.getPrograma().getCodeProgram())
                .nombrePrograma(c.getPrograma().getNameProgram())
                .codeCourse(c.getAsignatura().getCodeCourse())
                .nombreAsignatura(c.getNombreAsignatura())
                .credits(c.getCredits())
                .semestre(c.getSemestre())
                .jornada(c.getJornada())
                .numeroResolucion(c.getNumeroResolucion())
                .grupo(c.getGrupo())
                .idDocente(idDocente)
                .nombreDocente(nombreDocente)
                .estado(c.getEstado())
                .build();
    }
}
