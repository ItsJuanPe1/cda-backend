package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.ProgramaDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.model.enums.TipoIngreso;
import co.edu.univalle.palmira.repository.PensumAcademicoRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// HU-040: crear, editar y desactivar programas académicos (docs/contratos/HU-040.md)
@Service
@RequiredArgsConstructor
public class ProgramaService {

    static final String MSG_NO_ENCONTRADO = "Programa no encontrado";
    static final String MSG_CODIGO_REPETIDO = "Ya existe un programa con este código";
    static final String MSG_CODIGO_NO_EDITABLE = "El código del programa no se puede modificar";

    private final ProgramaRepository programaRepository;
    private final PensumAcademicoRepository pensumAcademicoRepository;

    // Criterio 5: el administrador ve todos los programas, activos e inactivos
    @Transactional(readOnly = true)
    public List<ProgramaDto.Response> getAll() {
        // Un solo GROUP BY en vez de un COUNT por cada programa
        Map<String, Long> conteos = new HashMap<>();
        for (Object[] fila : pensumAcademicoRepository.contarAsignaturasPorPrograma()) {
            conteos.put((String) fila[0], (Long) fila[1]);
        }
        return programaRepository.findAllByOrderByNameProgramAsc()
                .stream()
                .map(p -> toResponse(p, conteos.getOrDefault(p.getCodeProgram(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgramaDto.Response getByCode(String codeProgram) {
        return toResponseWithCount(buscar(codeProgram));
    }

    @Transactional
    public ProgramaDto.Response create(ProgramaDto.CreateRequest request) {
        String code = request.codeProgram().trim();
        if (programaRepository.existsById(code)) {
            throw new ConflictException(MSG_CODIGO_REPETIDO, null);
        }

        Jornada jornada = Jornada.valueOf(request.jornada());
        Integer diurna = jornada == Jornada.NOCTURNA ? null : request.numeroSemestre();
        Integer nocturna = jornada == Jornada.DIURNA ? null : request.semestresNocturna();
        validarDuraciones(jornada, diurna, nocturna);

        Programa programa = Programa.builder()
                .codeProgram(code)
                .nameProgram(request.nameProgram().trim())
                .jornada(jornada)
                .numeroSemestre(diurna)
                .semestresNocturna(nocturna)
                .ingreso(TipoIngreso.valueOf(request.ingreso()))
                .estado(request.estado() != null ? EstadoPrograma.valueOf(request.estado()) : EstadoPrograma.ACTIVO)
                .idFacultadPrograma(request.idFacultadPrograma())
                .apertura(normalizeApertura(request.apertura()))
                .build();

        // Criterio 3: un programa nuevo no tiene malla ni cohortes
        return toResponse(programaRepository.save(programa), 0L);
    }

    // Criterio 4: editar no toca mallas, cohortes ni coordinador (solo columnas de
    // programas)
    @Transactional
    public ProgramaDto.Response update(String codeProgram, ProgramaDto.UpdateRequest request) {
        Programa programa = buscar(codeProgram);

        if (request.codeProgram() != null && !request.codeProgram().trim().equals(programa.getCodeProgram())) {
            throw new BadRequestException(MSG_CODIGO_NO_EDITABLE);
        }

        Jornada jornada = request.jornada() != null ? Jornada.valueOf(request.jornada()) : programa.getJornada();
        Integer diurna = request.numeroSemestre() != null ? request.numeroSemestre() : programa.getNumeroSemestre();
        Integer nocturna = request.semestresNocturna() != null ? request.semestresNocturna()
                : programa.getSemestresNocturna();
        // La duración de la jornada que ya no se oferta queda vacía
        if (jornada == Jornada.DIURNA) {
            nocturna = null;
        } else if (jornada == Jornada.NOCTURNA) {
            diurna = null;
        }
        validarDuraciones(jornada, diurna, nocturna);

        if (request.nameProgram() != null) {
            programa.setNameProgram(request.nameProgram().trim());
        }
        programa.setJornada(jornada);
        programa.setNumeroSemestre(diurna);
        programa.setSemestresNocturna(nocturna);
        if (request.ingreso() != null) {
            programa.setIngreso(TipoIngreso.valueOf(request.ingreso()));
        }
        if (request.estado() != null) {
            programa.setEstado(EstadoPrograma.valueOf(request.estado()));
        }
        if (request.idFacultadPrograma() != null) {
            programa.setIdFacultadPrograma(request.idFacultadPrograma());
        }
        if (request.apertura() != null) {
            programa.setApertura(normalizeApertura(request.apertura()));
        }

        return toResponseWithCount(programaRepository.save(programa));
    }

    // RN-13: no se borra; se desactiva y se conservan malla, cohortes, coordinador
    // y docentes
    @Transactional
    public void desactivar(String codeProgram) {
        Programa programa = buscar(codeProgram);
        programa.setEstado(EstadoPrograma.INACTIVO);
        programaRepository.save(programa);
    }

    private Programa buscar(String codeProgram) {
        return programaRepository.findById(codeProgram.trim())
                .orElseThrow(() -> new ResourceNotFoundException(MSG_NO_ENCONTRADO));
    }

    private void validarDuraciones(Jornada jornada, Integer diurna, Integer nocturna) {
        if (jornada != Jornada.NOCTURNA && !enRango(diurna)) {
            throw new BadRequestException(ProgramaDto.MSG_DURACION_DIURNA);
        }
        if (jornada != Jornada.DIURNA && !enRango(nocturna)) {
            throw new BadRequestException(ProgramaDto.MSG_DURACION_NOCTURNA);
        }
    }

    private boolean enRango(Integer semestres) {
        return semestres != null && semestres >= 1 && semestres <= 20;
    }

    private ProgramaDto.Response toResponseWithCount(Programa p) {
        return toResponse(p, pensumAcademicoRepository.countByIdCodeProgram(p.getCodeProgram()));
    }

    private ProgramaDto.Response toResponse(Programa p, long totalAsignaturas) {
        return new ProgramaDto.Response(
                p.getCodeProgram(),
                p.getNameProgram(),
                p.getJornada(),
                p.getNumeroSemestre(),
                p.getSemestresNocturna(),
                p.getIngreso(),
                p.getEstado(),
                p.getIdFacultadPrograma(),
                totalAsignaturas,
                p.getApertura());
    }

    private String normalizeApertura(String apertura) {
        return apertura == null || apertura.isBlank() ? null : apertura.trim().toUpperCase();
    }
}