package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.PensumItemDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Asignatura;
import co.edu.univalle.palmira.model.entity.PensumAcademico;
import co.edu.univalle.palmira.model.entity.PensumId;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.repository.AsignaturaRepository;
import co.edu.univalle.palmira.repository.PensumAcademicoRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PensumService {

    private final PensumAcademicoRepository pensumRepository;
    private final ProgramaRepository programaRepository;
    private final AsignaturaRepository asignaturaRepository;

    @Transactional(readOnly = true)
    public List<PensumItemDto.Response> getByProgram(String codeProgram) {
        String normalizedCode = codeProgram.trim().toUpperCase();
        return pensumRepository.findByCodeProgramWithDetails(normalizedCode)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PensumItemDto.Response> getAll() {
        return pensumRepository.findAllWithAsignatura()
                .stream()
                .filter(p -> Boolean.TRUE.equals(p.getVigente()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PensumItemDto.Response addCourseToPensum(PensumItemDto.AddRequest request) {
        String progCode = request.codeProgram().trim().toUpperCase();
        String courseCode = request.codeCourse().trim().toUpperCase();
        int semestre = request.semestre();

        if (semestre < 1 || semestre > 20) {
            throw new BadRequestException("El semestre debe ser un número entero entre 1 y 20");
        }

        Programa programa = programaRepository.findById(progCode)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado: " + progCode));

        Asignatura asignatura = asignaturaRepository.findById(courseCode)
                .orElseThrow(() -> new ResourceNotFoundException("Asignatura no encontrada: " + courseCode));

        int version = currentVersion(progCode);
        PensumId id = new PensumId(progCode, courseCode, version);
        if (pensumRepository.existsById(id)) {
            throw new BadRequestException(
                    "La asignatura " + courseCode + " ya se encuentra asignada al programa " + progCode);
        }

        PensumAcademico pensum = PensumAcademico.builder()
                .id(id)
                .programa(programa)
                .asignatura(asignatura)
                .semestre(semestre)
                .isElective(asignatura.getIsElective())
                .facultad(asignatura.getFacultad())
                .activo(true)
                .vigente(true)
                .build();

        PensumAcademico saved = pensumRepository.save(pensum);
        return toResponse(saved);
    }

    @Transactional
    public PensumItemDto.Response updateCourseSemester(PensumItemDto.UpdateSemesterRequest request) {
        String progCode = request.codeProgram().trim().toUpperCase();
        String courseCode = request.codeCourse().trim().toUpperCase();
        int semestre = request.semestre();

        if (semestre < 1 || semestre > 20) {
            throw new BadRequestException("El semestre debe ser un número entero entre 1 y 20");
        }

        int version = currentVersion(progCode);
        PensumId id = new PensumId(progCode, courseCode, version);
        PensumAcademico pensum = pensumRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Asignación no encontrada para programa " + progCode + " y asignatura " + courseCode));

        pensum.setSemestre(semestre);
        PensumAcademico updated = pensumRepository.save(pensum);
        return toResponse(updated);
    }

    @Transactional
    public PensumItemDto.Response cambiarEstado(String codeProgram, String codeCourse, Boolean activo) {
        String progCode = codeProgram.trim().toUpperCase();
        String courseCode = codeCourse.trim().toUpperCase();
        int version = currentVersion(progCode);
        PensumId id = new PensumId(progCode, courseCode, version);

        PensumAcademico pensum = pensumRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Asignación no encontrada para programa " + progCode + " y asignatura " + courseCode));

        pensum.setActivo(Boolean.TRUE.equals(activo));
        PensumAcademico updated = pensumRepository.save(pensum);
        return toResponse(updated);
    }

    @Transactional
    public void removeCourseFromPensum(String codeProgram, String codeCourse) {
        String progCode = codeProgram.trim().toUpperCase();
        String courseCode = codeCourse.trim().toUpperCase();
        int version = currentVersion(progCode);
        PensumId id = new PensumId(progCode, courseCode, version);

        if (!pensumRepository.existsById(id)) {
            throw new ResourceNotFoundException(
                    "Asignación no encontrada para programa " + progCode + " y asignatura " + courseCode);
        }

        pensumRepository.deleteById(id);
    }

    @Transactional
    public List<PensumItemDto.Response> saveVersion(PensumItemDto.SaveRequest request) {
        String programCode = normalize(request.codeProgram());
        Programa programa = programaRepository.findById(programCode)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado: " + programCode));

        List<PensumAcademico> lockedRows = pensumRepository.lockByProgram(programCode);
        int version = (lockedRows.stream()
                .map(PensumAcademico::getId)
                .map(PensumId::getVersion)
                .max(Integer::compareTo)
                .orElse(0)) + 1;

        Map<String, PensumItemDto.SaveItem> uniqueItems = request.items().stream()
                .collect(Collectors.toMap(
                        item -> normalize(item.codeCourse()),
                        Function.identity(),
                        (first, ignored) -> first));

        lockedRows.stream()
                .filter(row -> Boolean.TRUE.equals(row.getVigente()))
                .forEach(row -> {
                    row.setVigente(false);
                    row.setFechaDesactivacion(LocalDateTime.now());
                });

        List<PensumAcademico> saved = uniqueItems.values().stream().map(item -> {
            String courseCode = normalize(item.codeCourse());
            Asignatura asignatura = asignaturaRepository.findById(courseCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Asignatura no encontrada: " + courseCode));
            return PensumAcademico.builder()
                    .id(new PensumId(programCode, courseCode, version))
                    .programa(programa)
                    .asignatura(asignatura)
                    .semestre(item.semestre())
                    .isElective(asignatura.getIsElective())
                    .facultad(asignatura.getFacultad())
                    .activo(true)
                    .vigente(true)
                    .build();
        }).toList();

        return pensumRepository.saveAll(saved).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PensumItemDto.HistorySummary> getHistory(String codeProgram) {
        String normalized = normalize(codeProgram);
        return pensumRepository.findByIdCodeProgramAndVigenteFalseOrderByFechaDesactivacionDesc(normalized)
                .stream()
                .collect(Collectors.groupingBy(
                        row -> row.getId().getVersion(),
                        java.util.LinkedHashMap::new,
                        Collectors.toList()))
                .values().stream()
                .map(rows -> new PensumItemDto.HistorySummary(
                        rows.get(0).getId().getVersion(),
                        rows.get(0).getFechaDesactivacion(),
                        rows.size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PensumItemDto.Response> getVersion(String codeProgram, Integer version) {
        return pensumRepository.findByIdCodeProgramAndIdVersion(normalize(codeProgram), version)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deactivateCurrent(String codeProgram) {
        String normalized = normalize(codeProgram);
        List<PensumAcademico> rows = pensumRepository.lockByProgram(normalized).stream()
                .filter(row -> Boolean.TRUE.equals(row.getVigente()))
                .toList();
        if (rows.isEmpty()) {
            throw new co.edu.univalle.palmira.exception.ConflictException("El programa no tiene una malla vigente",
                    null);
        }
        LocalDateTime now = LocalDateTime.now();
        rows.forEach(row -> {
            row.setVigente(false);
            row.setFechaDesactivacion(now);
        });
    }

    @Transactional
    public List<PensumItemDto.Response> reactivate(String codeProgram, Integer version) {
        String normalized = normalize(codeProgram);
        List<PensumAcademico> allRows = pensumRepository.lockByProgram(normalized);
        List<PensumAcademico> target = allRows.stream()
                .filter(row -> row.getId().getVersion().equals(version))
                .toList();
        if (target.isEmpty()) {
            throw new ResourceNotFoundException("Versión de malla no encontrada");
        }
        if (target.stream().allMatch(row -> Boolean.TRUE.equals(row.getVigente()))) {
            throw new co.edu.univalle.palmira.exception.ConflictException("La malla ya está vigente", null);
        }
        LocalDateTime now = LocalDateTime.now();
        allRows.stream().filter(row -> Boolean.TRUE.equals(row.getVigente())).forEach(row -> {
            row.setVigente(false);
            row.setFechaDesactivacion(now);
        });
        target.forEach(row -> {
            row.setVigente(true);
            row.setFechaDesactivacion(null);
            row.setFechaReactivacion(now);
        });
        return target.stream().map(this::toResponse).toList();
    }

    private int currentVersion(String codeProgram) {
        return pensumRepository.findByIdCodeProgramAndVigenteTrue(codeProgram).stream()
                .findFirst()
                .map(row -> row.getId().getVersion())
                .orElseGet(() -> Math.max(1, pensumRepository.findMaxVersion(codeProgram) + 1));
    }

    private String normalize(String value) {
        return value.trim().toUpperCase();
    }

    private PensumItemDto.Response toResponse(PensumAcademico p) {
        Asignatura a = p.getAsignatura();
        return new PensumItemDto.Response(
                p.getId().getCodeProgram(),
                p.getId().getCodeCourse(),
                a != null ? a.getNameCourse() : p.getId().getCodeCourse(),
                a != null ? a.getCredits() : 0,
                p.getSemestre(),
                a != null ? a.getIsElective() : p.getIsElective(),
                a != null ? a.getEsComplementaria() : false,
                a != null ? a.getEsBasica() : false,
                a != null ? a.getFacultad() : p.getFacultad(),
                p.getActivo() != null ? p.getActivo() : true,
                p.getId().getVersion(),
                p.getVigente() != null ? p.getVigente() : true);
    }
}