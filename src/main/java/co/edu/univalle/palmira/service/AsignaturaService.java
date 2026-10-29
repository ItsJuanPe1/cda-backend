package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.AsignaturaDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Asignatura;
import co.edu.univalle.palmira.repository.AsignaturaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AsignaturaService {

    private final AsignaturaRepository asignaturaRepository;

    @Transactional(readOnly = true)
    public List<AsignaturaDto.Response> getAll() {
        return asignaturaRepository.findAllByOrderByNameCourseAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AsignaturaDto.Response getByCode(String codeCourse) {
        Asignatura asignatura = asignaturaRepository.findById(codeCourse.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Asignatura no encontrada con código: " + codeCourse));
        return toResponse(asignatura);
    }

    @Transactional
    public AsignaturaDto.Response create(AsignaturaDto.CreateRequest request) {
        String normalizedCode = request.codeCourse().trim().toUpperCase();
        if (asignaturaRepository.existsById(normalizedCode)) {
            throw new BadRequestException("Ya existe una asignatura con el código: " + normalizedCode);
        }

        Asignatura asignatura = Asignatura.builder()
                .codeCourse(normalizedCode)
                .nameCourse(request.nameCourse().trim())
                .credits(request.credits() != null ? Math.max(0, request.credits()) : 0)
                .isElective(Boolean.TRUE.equals(request.isElective()))
                .esComplementaria(Boolean.TRUE.equals(request.esComplementaria()))
                .esBasica(Boolean.TRUE.equals(request.esBasica()))
                .facultad(request.facultad() != null ? request.facultad().trim() : null)
                .idFacultad(request.idFacultad())
                .build();

        return toResponse(asignaturaRepository.save(asignatura));
    }

    @Transactional
    public AsignaturaDto.Response update(String codeCourse, AsignaturaDto.UpdateRequest request) {
        String normalizedCode = codeCourse.trim().toUpperCase();
        Asignatura asignatura = asignaturaRepository.findById(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Asignatura no encontrada con código: " + normalizedCode));

        if (request.nameCourse() != null && !request.nameCourse().trim().isEmpty()) {
            asignatura.setNameCourse(request.nameCourse().trim());
        }
        if (request.credits() != null) {
            asignatura.setCredits(Math.max(0, request.credits()));
        }
        if (request.isElective() != null) {
            asignatura.setIsElective(request.isElective());
        }
        if (request.esComplementaria() != null) {
            asignatura.setEsComplementaria(request.esComplementaria());
        }
        if (request.esBasica() != null) {
            asignatura.setEsBasica(request.esBasica());
        }
        if (request.facultad() != null) {
            asignatura.setFacultad(request.facultad().trim());
        }
        if (request.idFacultad() != null) {
            asignatura.setIdFacultad(request.idFacultad());
        }
        if (request.activo() != null) {
            asignatura.setActivo(request.activo());
        }

        return toResponse(asignaturaRepository.save(asignatura));
    }

    @Transactional
    public AsignaturaDto.Response cambiarEstado(String codeCourse, Boolean activo) {
        String normalizedCode = codeCourse.trim().toUpperCase();
        Asignatura asignatura = asignaturaRepository.findById(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Asignatura no encontrada con código: " + normalizedCode));
        asignatura.setActivo(Boolean.TRUE.equals(activo));
        return toResponse(asignaturaRepository.save(asignatura));
    }

    @Transactional
    public void delete(String codeCourse) {
        String normalizedCode = codeCourse.trim().toUpperCase();
        if (!asignaturaRepository.existsById(normalizedCode)) {
            throw new ResourceNotFoundException("Asignatura no encontrada con código: " + normalizedCode);
        }
        asignaturaRepository.deleteById(normalizedCode);
    }

    private AsignaturaDto.Response toResponse(Asignatura a) {
        return new AsignaturaDto.Response(
                a.getCodeCourse(),
                a.getNameCourse(),
                a.getCredits(),
                a.getIsElective(),
                a.getEsComplementaria(),
                a.getEsBasica(),
                a.getFacultad(),
                a.getIdFacultad(),
                a.getActivo() != null ? a.getActivo() : true
        );
    }
}
