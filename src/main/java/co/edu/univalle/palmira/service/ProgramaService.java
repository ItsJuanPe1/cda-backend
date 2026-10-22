package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.ProgramaDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.repository.PensumAcademicoRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgramaService {

    private final ProgramaRepository programaRepository;
    private final PensumAcademicoRepository pensumAcademicoRepository;

    @Transactional(readOnly = true)
    public List<ProgramaDto.Response> getAll() {
        return programaRepository.findAllByOrderByNameProgramAsc()
                .stream()
                .map(this::toResponseWithCount)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgramaDto.Response getByCode(String codeProgram) {
        Programa programa = programaRepository.findById(codeProgram.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con código: " + codeProgram));
        return toResponseWithCount(programa);
    }

    @Transactional
    public ProgramaDto.Response create(ProgramaDto.CreateRequest request) {
        String normalizedCode = request.codeProgram().trim().toUpperCase();
        if (programaRepository.existsById(normalizedCode)) {
            throw new BadRequestException("Ya existe un programa con el código: " + normalizedCode);
        }

        Programa programa = Programa.builder()
                .codeProgram(normalizedCode)
                .nameProgram(request.nameProgram().trim())
                .numeroSemestre(request.numeroSemestre() != null ? request.numeroSemestre() : 10)
                .idFacultadPrograma(request.idFacultadPrograma())
                .build();

        Programa saved = programaRepository.save(programa);
        return new ProgramaDto.Response(
                saved.getCodeProgram(),
                saved.getNameProgram(),
                saved.getNumeroSemestre(),
                saved.getIdFacultadPrograma(),
                0L
        );
    }

    @Transactional
    public ProgramaDto.Response update(String codeProgram, ProgramaDto.UpdateRequest request) {
        String normalizedCode = codeProgram.trim().toUpperCase();
        Programa programa = programaRepository.findById(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con código: " + normalizedCode));

        if (request.nameProgram() != null && !request.nameProgram().trim().isEmpty()) {
            programa.setNameProgram(request.nameProgram().trim());
        }
        if (request.numeroSemestre() != null) {
            programa.setNumeroSemestre(request.numeroSemestre());
        }
        if (request.idFacultadPrograma() != null) {
            programa.setIdFacultadPrograma(request.idFacultadPrograma());
        }

        Programa updated = programaRepository.save(programa);
        return toResponseWithCount(updated);
    }

    @Transactional
    public void delete(String codeProgram) {
        String normalizedCode = codeProgram.trim().toUpperCase();
        if (!programaRepository.existsById(normalizedCode)) {
            throw new ResourceNotFoundException("Programa no encontrado con código: " + normalizedCode);
        }
        programaRepository.deleteById(normalizedCode);
    }

    private ProgramaDto.Response toResponseWithCount(Programa p) {
        long count = pensumAcademicoRepository.countByIdCodeProgram(p.getCodeProgram());
        return new ProgramaDto.Response(
                p.getCodeProgram(),
                p.getNameProgram(),
                p.getNumeroSemestre(),
                p.getIdFacultadPrograma(),
                count
        );
    }
}
