package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.CohorteDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.CohortePeriodo;
import co.edu.univalle.palmira.model.entity.PensumAcademico;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.repository.CohortePeriodoRepository;
import co.edu.univalle.palmira.repository.PensumAcademicoRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CohorteService {

    private final CohortePeriodoRepository cohorteRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final ProgramaRepository programaRepository;
    private final PensumAcademicoRepository pensumRepository;

    @Transactional(readOnly = true)
    public List<CohorteDto.Response> obtenerCohortesPorPeriodo(Long periodoId) {
        validarPeriodo(periodoId);
        return cohorteRepository.findByPeriodoIdWithPrograma(periodoId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CohorteDto.Response> obtenerCohortesPorPeriodoYPrograma(Long periodoId, String codeProgram) {
        validarPeriodo(periodoId);
        validarPrograma(codeProgram);
        return cohorteRepository.findByPeriodoIdAndCodeProgram(periodoId, codeProgram).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<CohorteDto.Response> guardarCohortes(Long periodoId, String codeProgram, List<CohorteDto.Request> requests) {
        PeriodoAcademico periodo = validarPeriodo(periodoId);
        Programa programa = validarPrograma(codeProgram);

        int duracionDiurna = programa.getNumeroSemestre() != null ? programa.getNumeroSemestre() : 10;
        int duracionNocturna = programa.getSemestresNocturna() != null ? programa.getSemestresNocturna() : duracionDiurna;

        List<CohortePeriodo> nuevasCohortes = new ArrayList<>();
        Set<String> unicos = new HashSet<>();

        for (CohorteDto.Request req : requests) {
            String clave = req.getSemestre() + "-" + req.getJornada();
            if (!unicos.add(clave)) {
                throw new BadRequestException("No puede haber cohortes duplicadas para el semestre " + req.getSemestre() + " y jornada " + req.getJornada());
            }

            int duracionPermitida = req.getJornada() == Jornada.NOCTURNA ? duracionNocturna : duracionDiurna;
            if (req.getSemestre() > duracionPermitida) {
                throw new BadRequestException("El semestre " + req.getSemestre() + " supera la duración máxima permitida (" + duracionPermitida + ") para la jornada " + req.getJornada());
            }

            // Validar que la resolución tenga materias activas en ese semestre
            List<PensumAcademico> materias = pensumRepository.findActiveByCodeProgramAndResolucionAndSemestre(
                    codeProgram, req.getNumeroResolucion(), req.getSemestre());
            if (materias.isEmpty()) {
                throw new BadRequestException("La resolución '" + req.getNumeroResolucion() + "' no tiene asignaturas activas para el semestre " + req.getSemestre() + " del programa " + programa.getNameProgram());
            }

            nuevasCohortes.add(CohortePeriodo.builder()
                    .periodo(periodo)
                    .programa(programa)
                    .semestre(req.getSemestre())
                    .jornada(req.getJornada())
                    .numeroResolucion(req.getNumeroResolucion())
                    .confirmada(req.getConfirmada() != null ? req.getConfirmada() : true)
                    .build());
        }

        cohorteRepository.deleteByPeriodoIdAndCodeProgram(periodoId, codeProgram);
        List<CohortePeriodo> guardadas = cohorteRepository.saveAll(nuevasCohortes);

        return guardadas.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CohorteDto.PropuestaResponse> proponerSiguientePeriodo(Long periodoActualId, Long periodoAnteriorId, boolean confirmarAvanceForzado) {
        validarPeriodo(periodoActualId);

        String advertenciaPeriodoAnterior = null;
        if (periodoAnteriorId != null) {
            PeriodoAcademico periodoAnterior = periodoRepository.findById(periodoAnteriorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Período anterior no encontrado"));
            if ("DESACTIVADO".equalsIgnoreCase(periodoAnterior.getEstado()) && !confirmarAvanceForzado) {
                advertenciaPeriodoAnterior = "El período anterior está marcado como DESACTIVADO o cancelado. Se requiere confirmación para proponer el avance.";
            }
        }

        List<Programa> programasActivos = programaRepository.findByEstadoOrderByNameProgramAsc(EstadoPrograma.ACTIVO);
        List<CohorteDto.PropuestaResponse> propuestas = new ArrayList<>();

        for (Programa prog : programasActivos) {
            int duracionDiurna = prog.getNumeroSemestre() != null ? prog.getNumeroSemestre() : 10;
            int duracionNocturna = prog.getSemestresNocturna() != null ? prog.getSemestresNocturna() : duracionDiurna;

            // Obtener resolución vigente de la malla más reciente
            List<PensumAcademico> vigentePensum = pensumRepository.findByIdCodeProgramAndVigenteTrue(prog.getCodeProgram());
            String resolucionVigente = vigentePensum.stream()
                    .map(PensumAcademico::getNumeroResolucion)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(prog.getNumeroResolucion() != null ? prog.getNumeroResolucion() : "Malla Inicial");

            List<CohorteDto.PropuestaCohorteItem> itemsPropuestos = new ArrayList<>();

            if (advertenciaPeriodoAnterior != null) {
                // Si hay advertencia y no se confirmó avance forzado, no propone avance automático
                propuestas.add(CohorteDto.PropuestaResponse.builder()
                        .codeProgram(prog.getCodeProgram())
                        .nombrePrograma(prog.getNameProgram())
                        .jornada(prog.getJornada())
                        .duracionDiurna(duracionDiurna)
                        .duracionNocturna(duracionNocturna)
                        .ingreso(prog.getIngreso() != null ? prog.getIngreso().name() : "SEMESTRAL")
                        .advertenciaPeriodoAnterior(advertenciaPeriodoAnterior)
                        .cohortesPropuestas(Collections.emptyList())
                        .build());
                continue;
            }

            // Buscar cohortes del periodo anterior para este programa
            List<CohortePeriodo> cohortesAnteriores = periodoAnteriorId != null
                    ? cohorteRepository.findByPeriodoIdAndCodeProgram(periodoAnteriorId, prog.getCodeProgram())
                    : Collections.emptyList();

            if (!cohortesAnteriores.isEmpty()) {
                // Rotación de cohortes: c -> c + 1
                for (CohortePeriodo anterior : cohortesAnteriores) {
                    int duracionMax = anterior.getJornada() == Jornada.NOCTURNA ? duracionNocturna : duracionDiurna;
                    int siguienteSemestre = anterior.getSemestre() + 1;

                    if (siguienteSemestre <= duracionMax) {
                        boolean enExtincion = !anterior.getNumeroResolucion().equalsIgnoreCase(resolucionVigente);
                        itemsPropuestos.add(CohorteDto.PropuestaCohorteItem.builder()
                                .semestre(siguienteSemestre)
                                .jornada(anterior.getJornada())
                                .numeroResolucion(anterior.getNumeroResolucion())
                                .enExtincion(enExtincion)
                                .build());
                    }
                }

                // Si tiene ingreso semestral o una cohorte llegó al final, se propone nuevo ingreso en semestre 1
                boolean incluyeSemestre1 = itemsPropuestos.stream().anyMatch(i -> i.getSemestre() == 1);
                if (!incluyeSemestre1 && ("SEMESTRAL".equalsIgnoreCase(String.valueOf(prog.getIngreso())) || prog.getIngreso() == null)) {
                    Jornada jornadaIngreso = prog.getJornada() == Jornada.NOCTURNA ? Jornada.NOCTURNA : Jornada.DIURNA;
                    itemsPropuestos.add(0, CohorteDto.PropuestaCohorteItem.builder()
                            .semestre(1)
                            .jornada(jornadaIngreso)
                            .numeroResolucion(resolucionVigente)
                            .enExtincion(false)
                            .build());
                }
            } else {
                // No tenía cohortes previas: propuesta base
                Jornada j = prog.getJornada() == Jornada.NOCTURNA ? Jornada.NOCTURNA : Jornada.DIURNA;
                itemsPropuestos.add(CohorteDto.PropuestaCohorteItem.builder()
                        .semestre(1)
                        .jornada(j)
                        .numeroResolucion(resolucionVigente)
                        .enExtincion(false)
                        .build());
            }

            // Ordenar por semestre y jornada
            itemsPropuestos.sort(Comparator.comparingInt(CohorteDto.PropuestaCohorteItem::getSemestre));

            propuestas.add(CohorteDto.PropuestaResponse.builder()
                    .codeProgram(prog.getCodeProgram())
                    .nombrePrograma(prog.getNameProgram())
                    .jornada(prog.getJornada())
                    .duracionDiurna(duracionDiurna)
                    .duracionNocturna(duracionNocturna)
                    .ingreso(prog.getIngreso() != null ? prog.getIngreso().name() : "SEMESTRAL")
                    .advertenciaPeriodoAnterior(null)
                    .cohortesPropuestas(itemsPropuestos)
                    .build());
        }

        return propuestas;
    }

    private PeriodoAcademico validarPeriodo(Long periodoId) {
        return periodoRepository.findById(periodoId)
                .orElseThrow(() -> new ResourceNotFoundException("Período académico no encontrado con ID: " + periodoId));
    }

    private Programa validarPrograma(String codeProgram) {
        return programaRepository.findById(codeProgram)
                .orElseThrow(() -> new ResourceNotFoundException("Programa académico no encontrado con código: " + codeProgram));
    }

    private CohorteDto.Response toResponse(CohortePeriodo c) {
        return CohorteDto.Response.builder()
                .id(c.getId())
                .periodoId(c.getPeriodo().getId())
                .codeProgram(c.getPrograma().getCodeProgram())
                .nombrePrograma(c.getPrograma().getNameProgram())
                .semestre(c.getSemestre())
                .jornada(c.getJornada())
                .numeroResolucion(c.getNumeroResolucion())
                .confirmada(c.getConfirmada())
                .build();
    }
}
