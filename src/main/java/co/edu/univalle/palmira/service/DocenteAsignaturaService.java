package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.DocenteAsignaturaDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.*;
import co.edu.univalle.palmira.model.enums.OrigenAsociacion;
import co.edu.univalle.palmira.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * HU-049: asociar docentes a las asignaturas que han dictado.
 * El histórico no se borra físicamente: quitar una asociación la desactiva (RN-13).
 * La asociación queda ligada a la facultad de adscripción del docente.
 */
@Service
@RequiredArgsConstructor
public class DocenteAsignaturaService {

    static final String MSG_DOCENTE_NO_EXISTE = "No existe un docente con el id indicado";
    static final String MSG_DOCENTE_INACTIVO = "El docente está inactivo";
    static final String MSG_ASIGNATURA_NO_EXISTE = "No existe una asignatura con el código indicado";
    static final String MSG_ASIGNATURA_INACTIVA = "La asignatura está inactiva";
    static final String MSG_ASIGNATURA_FUERA_DE_FACULTAD =
            "La asignatura no corresponde a la facultad de adscripción del docente";
    static final String MSG_SIN_FACULTAD =
            "El docente no tiene facultad de adscripción para validar su perfil";
    static final String MSG_ASOCIACION_EXISTE =
            "Ya existe la asociación del docente con la asignatura en su facultad";

    private final DocenteAsignaturaRepository asociacionRepository;
    private final DocenteRepository docenteRepository;
    private final AsignaturaRepository asignaturaRepository;
    private final ProgramaRepository programaRepository;
    private final PensumAcademicoRepository pensumRepository;
    private final FacultadRepository facultadRepository;

    // ---------------------------------------------------------------
    // Criterio 5: consulta por docente, asignatura, facultad o estado
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<DocenteAsignaturaDto.Response> listar(Long docenteId, String codeCourse,
                                                       Integer idFacultad, Boolean activo) {
        String curso = normalizarCodigo(codeCourse);
        return asociacionRepository.buscar(docenteId, curso, idFacultad, activo)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ---------------------------------------------------------------
    // Criterio 1: asociar individualmente
    // ---------------------------------------------------------------
    @Transactional
    public DocenteAsignaturaDto.Response asociar(DocenteAsignaturaDto.CreateRequest request) {
        Docente docente = docenteRepository.findByIdWithDetails(request.docenteId())
                .orElseThrow(() -> new ResourceNotFoundException(MSG_DOCENTE_NO_EXISTE));
        Facultad facultad = resolverFacultad(docente);
        Resultado resultado = procesarAsociacion(
                docente,
                request.codeCourse(),
                facultad,
                OrigenAsociacion.MANUAL,
                null);
        return toResponse(resultado.asociacion());
    }

    // ---------------------------------------------------------------
    // Criterio 1: quitar (desactivar) individualmente. No borra (RN-13).
    // ---------------------------------------------------------------
    @Transactional
    public DocenteAsignaturaDto.Response quitar(Long id) {
        return cambiarEstado(id, false);
    }

    @Transactional
    public DocenteAsignaturaDto.Response cambiarEstado(Long id, boolean activo) {
        DocenteAsignatura asociacion = asociacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asociación no encontrada con id " + id));
        asociacion.setActivo(activo);
        asociacion.setDesactivadoAt(activo ? null : Instant.now());
        return toResponse(asociacionRepository.save(asociacion));
    }

    @Transactional
    public void desactivarAsociacion(Docente docente, CursoProgramacion curso) {
        Facultad facultad = resolverFacultadDeCurso(curso);
        if (facultad == null) {
            return;
        }
        asociacionRepository.findByDocenteIdAndAsignaturaCodeCourseAndFacultadId(
                        docente.getId(), curso.getAsignatura().getCodeCourse(), facultad.getId())
                .ifPresent(asociacion -> {
                    asociacion.setActivo(false);
                    asociacion.setDesactivadoAt(Instant.now());
                    asociacionRepository.save(asociacion);
                });
    }

    // ---------------------------------------------------------------
    // Criterio 4: si un docente dicta una asignatura en un periodo, se
    // agrega al histórico automáticamente. Lo llama ProgramacionService y
    // además existe el trigger de la migración V27 como respaldo.
    // ---------------------------------------------------------------
    @Transactional
    public void registrarDesdeCursos(List<CursoProgramacion> cursos) {
        if (cursos == null) {
            return;
        }
        for (CursoProgramacion curso : cursos) {
            if (curso.getDocente() == null || curso.getAsignatura() == null) {
                continue;
            }
            Facultad facultad = resolverFacultadDeCurso(curso);
            if (facultad == null) {
                continue;
            }
            DocenteAsignatura asociacion = asociacionRepository
                    .findByDocenteIdAndAsignaturaCodeCourseAndFacultadId(
                            curso.getDocente().getId(),
                            curso.getAsignatura().getCodeCourse(),
                            facultad.getId())
                    .orElseGet(() -> DocenteAsignatura.builder()
                            .docente(curso.getDocente())
                            .asignatura(curso.getAsignatura())
                            .facultad(facultad)
                            .origen(OrigenAsociacion.PROGRAMACION)
                            .build());
            asociacion.setActivo(true);
            asociacion.setDesactivadoAt(null);
            asociacion.setPeriodo(curso.getPeriodo());
            asociacion.setUpdatedAt(Instant.now());
            asociacionRepository.save(asociacion);
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private Resultado procesarAsociacion(Docente docente, String codeCourse, Facultad facultad,
                                          OrigenAsociacion origen, PeriodoAcademico periodo) {
        if (!Boolean.TRUE.equals(docente.getEstado())) {
            throw new BadRequestException(MSG_DOCENTE_INACTIVO);
        }

        String curso = normalizarCodigoObligatorio(codeCourse, "La asignatura es obligatoria");

        Asignatura asignatura = asignaturaRepository.findById(curso)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_ASIGNATURA_NO_EXISTE));
        if (!Boolean.TRUE.equals(asignatura.getActivo())) {
            throw new BadRequestException(MSG_ASIGNATURA_INACTIVA);
        }

        validarAsignaturaDeFacultad(asignatura, facultad);

        DocenteAsignatura existente = asociacionRepository
                .findByDocenteIdAndAsignaturaCodeCourseAndFacultadId(
                        docente.getId(), curso, facultad.getId())
                .orElse(null);

        if (existente != null && Boolean.TRUE.equals(existente.getActivo())) {
            throw new ConflictException(MSG_ASOCIACION_EXISTE, null);
        }

        boolean reactivada = existente != null;
        DocenteAsignatura asociacion = existente != null
                ? existente
                : DocenteAsignatura.builder()
                        .docente(docente)
                        .asignatura(asignatura)
                        .facultad(facultad)
                        .build();
        asociacion.setActivo(true);
        asociacion.setDesactivadoAt(null);
        asociacion.setOrigen(origen);
        asociacion.setPeriodo(periodo);
        asociacion.setUpdatedAt(Instant.now());

        return new Resultado(asociacionRepository.save(asociacion), reactivada);
    }

    /**
     * Criterio 3: el docente solo se asocia a asignaturas de su facultad.
     * Se acepta si la asignatura está marcada con esa facultad o si aparece en
     * la malla de alguno de los programas de la facultad.
     */
    private void validarAsignaturaDeFacultad(Asignatura asignatura, Facultad facultad) {
        if (Objects.equals(asignatura.getIdFacultad(), facultad.getId())) {
            return;
        }

        List<String> programasFacultad = programaRepository
                .findCodeProgramByIdFacultadPrograma(facultad.getId());
        if (!programasFacultad.isEmpty()
                && pensumRepository.existsByIdCodeProgramInAndIdCodeCourse(
                        programasFacultad, asignatura.getCodeCourse())) {
            return;
        }

        throw new BadRequestException(MSG_ASIGNATURA_FUERA_DE_FACULTAD);
    }

    private Facultad resolverFacultad(Docente docente) {
        if (docente.getFacultad() != null) {
            return docente.getFacultad();
        }
        if (docente.getPrograma() != null && docente.getPrograma().getIdFacultadPrograma() != null) {
            return facultadRepository.findById(docente.getPrograma().getIdFacultadPrograma())
                    .orElseThrow(() -> new BadRequestException(MSG_SIN_FACULTAD));
        }
        throw new BadRequestException(MSG_SIN_FACULTAD);
    }

    private Facultad resolverFacultadDeCurso(CursoProgramacion curso) {
        Integer idFacultad = null;
        if (curso.getPrograma() != null) {
            idFacultad = curso.getPrograma().getIdFacultadPrograma();
        }
        if (idFacultad == null && curso.getAsignatura() != null) {
            idFacultad = curso.getAsignatura().getIdFacultad();
        }
        if (idFacultad == null) {
            return null;
        }
        return facultadRepository.findById(idFacultad).orElse(null);
    }

    private static String normalizarCodigo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizarCodigoObligatorio(String valor, String mensaje) {
        String normalizado = normalizarCodigo(valor);
        if (normalizado == null) {
            throw new BadRequestException(mensaje);
        }
        return normalizado;
    }

    private DocenteAsignaturaDto.Response toResponse(DocenteAsignatura da) {
        Docente docente = da.getDocente();
        Asignatura asignatura = da.getAsignatura();
        Facultad facultad = da.getFacultad();
        PeriodoAcademico periodo = da.getPeriodo();

        return new DocenteAsignaturaDto.Response(
                da.getId(),
                docente != null ? docente.getId() : null,
                docente != null ? docente.getCodigo() : null,
                docente != null ? (docente.getNombres() + " " + docente.getApellidos()).trim() : null,
                docente != null ? docente.getCorreoInstitucional() : null,
                docente != null ? docente.getNumeroDocumento() : null,
                asignatura != null ? asignatura.getCodeCourse() : null,
                asignatura != null ? asignatura.getNameCourse() : null,
                facultad != null ? facultad.getId() : null,
                facultad != null ? facultad.getNombre() : null,
                periodo != null ? periodo.getId() : null,
                periodo != null ? periodo.getNombre() : null,
                da.getOrigen(),
                da.getActivo(),
                da.getCreatedAt(),
                da.getUpdatedAt(),
                da.getDesactivadoAt());
    }

    private record Resultado(DocenteAsignatura asociacion, boolean reactivada) {
    }
}
