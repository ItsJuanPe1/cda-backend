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
        return pensumRepository.findAll()
                .stream()
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

        PensumId id = new PensumId(progCode, courseCode);
        if (pensumRepository.existsById(id)) {
            throw new BadRequestException("La asignatura " + courseCode + " ya se encuentra asignada al programa " + progCode);
        }

        PensumAcademico pensum = PensumAcademico.builder()
                .id(id)
                .programa(programa)
                .asignatura(asignatura)
                .semestre(semestre)
                .isElective(asignatura.getIsElective())
                .facultad(asignatura.getFacultad())
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

        PensumId id = new PensumId(progCode, courseCode);
        PensumAcademico pensum = pensumRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Asignación no encontrada para programa " + progCode + " y asignatura " + courseCode));

        pensum.setSemestre(semestre);
        PensumAcademico updated = pensumRepository.save(pensum);
        return toResponse(updated);
    }

    @Transactional
    public void removeCourseFromPensum(String codeProgram, String codeCourse) {
        String progCode = codeProgram.trim().toUpperCase();
        String courseCode = codeCourse.trim().toUpperCase();
        PensumId id = new PensumId(progCode, courseCode);

        if (!pensumRepository.existsById(id)) {
            throw new ResourceNotFoundException(
                    "Asignación no encontrada para programa " + progCode + " y asignatura " + courseCode);
        }

        pensumRepository.deleteById(id);
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
                a != null ? a.getFacultad() : p.getFacultad()
        );
    }
}
