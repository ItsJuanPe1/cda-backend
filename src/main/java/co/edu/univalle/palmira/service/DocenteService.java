package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.DocenteDto;
import co.edu.univalle.palmira.dto.EvaluacionDocenteDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.CursoProgramacion;
import co.edu.univalle.palmira.model.entity.Facultad;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.entity.EvaluacionDocente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.repository.EvaluacionDocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import co.edu.univalle.palmira.repository.CursoProgramacionRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.FacultadRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocenteService {

    // HU-015: mensajes de "Mi perfil"
    public static final String MSG_SIN_CAMBIOS = "No hay cambios para guardar";
    public static final String MSG_PERFIL_NO_ENCONTRADO = "No se encontró su perfil de docente";

    private final DocenteRepository docenteRepository;
    private final ProgramaRepository programaRepository;
    private final FacultadRepository facultadRepository;
    private final EvaluacionDocenteRepository evaluacionDocenteRepository;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final CursoProgramacionRepository cursoProgramacionRepository;
    private final DocenteAsignaturaService docenteAsignaturaService;

    // ---------------------------------------------------------------
    // HU-026: Crear perfil de docente
    // ---------------------------------------------------------------
    @Transactional
    public DocenteDto.Response crear(DocenteDto.CreateRequest req) {
        // Documento duplicado: el front ofrece actualizar (necesita el id existente)
        docenteRepository.findByNumeroDocumento(req.numeroDocumento())
                .ifPresent(existente -> {
                    throw new ConflictException(
                            "Ya existe un docente con el documento " + req.numeroDocumento(),
                            existente.getId());
                });

        // Código y correo duplicados: 409 sin id existente
        if (docenteRepository.existsByCodigo(req.codigo())) {
            throw new ConflictException(
                    "Ya existe un docente con el código " + req.codigo(), null);
        }
        if (docenteRepository.existsByCorreoInstitucional(req.correoInstitucional())) {
            throw new ConflictException(
                    "Ya existe un docente con el correo " + req.correoInstitucional(), null);
        }

        Facultad facultad = null;
        Programa programa = null;

        if (req.idFacultad() != null) {
            facultad = buscarFacultad(req.idFacultad());
        } else if (req.codeProgram() != null && !req.codeProgram().isBlank()) {
            programa = buscarPrograma(req.codeProgram());
            if (programa.getIdFacultadPrograma() != null) {
                facultad = facultadRepository.findById(programa.getIdFacultadPrograma()).orElse(null);
            }
        } else {
            throw new BadRequestException("La facultad de adscripción es obligatoria");
        }

        Docente docente = Docente.builder()
                .codigo(req.codigo().trim())
                .nombres(req.nombres().trim())
                .apellidos(req.apellidos().trim())
                .tipoDocumento(req.tipoDocumento())
                .numeroDocumento(req.numeroDocumento())
                .correoInstitucional(req.correoInstitucional().trim().toLowerCase())
                .telefono(req.telefono())
                .tipoDocente(req.tipoDocente())
                .categoria(req.categoria())
                .facultad(facultad)
                .programa(programa)
                .fotoUrl(req.fotoUrl())
                .build();

        return toResponse(docenteRepository.save(docente));
    }

    // ---------------------------------------------------------------
    // Actualizar (flujo "ya existe, ¿desea actualizarla?")
    // Los campos null no se modifican.
    // ---------------------------------------------------------------
    @Transactional
    public DocenteDto.Response actualizar(Long id, DocenteDto.UpdateRequest req) {
        Docente docente = docenteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Docente no encontrado con id " + id));

        if (req.codigo() != null && !req.codigo().equals(docente.getCodigo())) {
            if (docenteRepository.existsByCodigo(req.codigo())) {
                throw new ConflictException("Ya existe un docente con el código " + req.codigo(), null);
            }
            docente.setCodigo(req.codigo().trim());
        }

        if (req.numeroDocumento() != null && !req.numeroDocumento().equals(docente.getNumeroDocumento())) {
            docenteRepository.findByNumeroDocumento(req.numeroDocumento())
                    .ifPresent(otro -> {
                        throw new ConflictException(
                                "Ya existe un docente con el documento " + req.numeroDocumento(),
                                otro.getId());
                    });
            docente.setNumeroDocumento(req.numeroDocumento());
        }

        if (req.correoInstitucional() != null
                && !req.correoInstitucional().equalsIgnoreCase(docente.getCorreoInstitucional())) {
            if (docenteRepository.existsByCorreoInstitucional(req.correoInstitucional())) {
                throw new ConflictException(
                        "Ya existe un docente con el correo " + req.correoInstitucional(), null);
            }
            docente.setCorreoInstitucional(req.correoInstitucional().trim().toLowerCase());
        }

        if (req.nombres() != null)
            docente.setNombres(req.nombres().trim());
        if (req.apellidos() != null)
            docente.setApellidos(req.apellidos().trim());
        if (req.tipoDocumento() != null)
            docente.setTipoDocumento(req.tipoDocumento());
        if (req.telefono() != null)
            docente.setTelefono(req.telefono());
        if (req.tipoDocente() != null)
            docente.setTipoDocente(req.tipoDocente());
        if (req.categoria() != null)
            docente.setCategoria(req.categoria());
        if (req.fotoUrl() != null)
            docente.setFotoUrl(req.fotoUrl());

        if (req.idFacultad() != null) {
            docente.setFacultad(buscarFacultad(req.idFacultad()));
        } else if (req.codeProgram() != null && !req.codeProgram().isBlank()) {
            docente.setPrograma(buscarPrograma(req.codeProgram()));
        }

        return toResponse(docenteRepository.save(docente));
    }

    // ---------------------------------------------------------------
    // Listar todos los docentes para Plantilla
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<DocenteDto.Response> listarTodos() {
        return docenteRepository.findAllWithPrograma()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ---------------------------------------------------------------
    // Activar / Inactivar docente
    // ---------------------------------------------------------------
    @Transactional
    public DocenteDto.Response cambiarEstado(Long id, Boolean estado) {
        Docente docente = docenteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Docente no encontrado con id " + id));
        docente.setEstado(estado != null ? estado : true);
        return toResponse(docenteRepository.save(docente));
    }

    // ---------------------------------------------------------------
    // HU-022: Mi perfil (el correo sale siempre del JWT, nunca de la petición)
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public DocenteDto.Response obtenerPorCorreo(String correo) {
        return toResponse(buscarPerfilPropio(correo));
    }

    // ---------------------------------------------------------------
    // HU-015: Editar mis datos de contacto (correo personal y teléfono) y facultad.
    // Nombres, apellidos, correo institucional y demás datos no se editan aquí.
    // ---------------------------------------------------------------
    @Transactional
    public DocenteDto.Response actualizarContacto(String correo, DocenteDto.ContactoRequest req) {
        Docente docente = buscarPerfilPropio(correo);

        String correoPersonal = normalizarOpcional(req.correoPersonal());
        if (correoPersonal != null) {
            correoPersonal = correoPersonal.toLowerCase();
        }
        String telefono = normalizarOpcional(req.telefono());
        String celular = normalizarOpcional(req.celular());

        Integer idFacultadActual = docente.getFacultad() != null ? docente.getFacultad().getId() : null;
        Integer nuevoIdFacultad = req.idFacultad();
        boolean cambioFacultad = nuevoIdFacultad != null && !Objects.equals(nuevoIdFacultad, idFacultadActual);

        boolean cambioContacto = !Objects.equals(correoPersonal, docente.getCorreoPersonal())
                || !Objects.equals(telefono, docente.getTelefono())
                || !Objects.equals(celular, docente.getCelular());

        if (!cambioContacto && !cambioFacultad) {
            throw new BadRequestException(MSG_SIN_CAMBIOS);
        }

        if (cambioFacultad) {
            docente.setFacultad(buscarFacultad(nuevoIdFacultad));
        }
        docente.setCorreoPersonal(correoPersonal);
        docente.setTelefono(telefono);
        docente.setCelular(celular);
        return toResponse(docenteRepository.save(docente));
    }

    // ---------------------------------------------------------------
    // Listado de docentes (ordenado por apellidos y nombres)
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<DocenteDto.Response> listar() {
        return docenteRepository.findAllByOrderByApellidosAscNombresAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Devuelve la calificación de cada docente en la última edición anterior
     * al período vigente para la asignatura solicitada.
     */
    @Transactional(readOnly = true)
    public List<EvaluacionDocenteDto> listarEvaluacionesAnteriores(Long periodoId, String codeCourse) {
        PeriodoAcademico actual = periodoAcademicoRepository.findById(periodoId)
                .orElseThrow(() -> new BadRequestException("El período académico no existe"));
        PeriodoAcademico anterior = periodoAcademicoRepository
                .findAllByOrderByFechaInicioSemestreDesc()
                .stream()
                .filter(p -> p.getFechaInicioSemestre() != null
                        && p.getFechaInicioSemestre().isBefore(actual.getFechaInicioSemestre()))
                .findFirst()
                .orElse(null);
        if (anterior == null) {
            return List.of();
        }

        return evaluacionDocenteRepository
                .findAllByPeriodoIdAndAsignaturaCodeCourse(anterior.getId(), codeCourse)
                .stream()
                .map(this::toEvaluacionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EvaluacionDocenteDto.Curso> listarCursosEvaluacion(Long docenteId, Long periodoId) {
        Docente docente = docenteRepository.findById(docenteId)
                .orElseThrow(() -> new ResourceNotFoundException("Docente no encontrado"));
        PeriodoAcademico anterior = buscarPeriodoAnterior(periodoId);
        if (anterior == null) {
            return List.of();
        }
        return cursoProgramacionRepository.findByPeriodoIdAndDocenteId(anterior.getId(), docenteId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        curso -> curso.getAsignatura().getCodeCourse(),
                        curso -> curso,
                        (first, ignored) -> first))
                .values()
                .stream()
                .map(curso -> {
                    EvaluacionDocente evaluacion = evaluacionDocenteRepository
                            .findByDocenteIdAndAsignaturaCodeCourseAndPeriodoId(
                                    docenteId, curso.getAsignatura().getCodeCourse(), anterior.getId())
                            .orElse(null);
                    return new EvaluacionDocenteDto.Curso(
                            docenteId,
                            anterior.getId(),
                            anterior.getNombre(),
                            curso.getAsignatura().getCodeCourse(),
                            curso.getAsignatura().getNameCourse(),
                            evaluacion == null ? null : evaluacion.getCalificacion(),
                            evaluacion != null);
                })
                .toList();
    }

    @Transactional
    public EvaluacionDocenteDto.Resumen guardarEvaluacion(
            Long docenteId, EvaluacionDocenteDto.Request request) {
        if (request.codeCourse() == null || request.codeCourse().isBlank()) {
            throw new BadRequestException("La asignatura es obligatoria");
        }
        if (request.calificacion() == null
                || request.calificacion().compareTo(BigDecimal.ZERO) < 0
                || request.calificacion().compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new BadRequestException("La calificación debe estar entre 0 y 5");
        }
        Docente docente = docenteRepository.findById(docenteId)
                .orElseThrow(() -> new ResourceNotFoundException("Docente no encontrado"));
        PeriodoAcademico anterior = buscarPeriodoAnterior(request.periodoId());
        if (anterior == null) {
            throw new BadRequestException("No existe un período anterior para registrar la calificación");
        }

        List<CursoProgramacion> cursos = cursoProgramacionRepository
                .findByPeriodoIdAndDocenteId(anterior.getId(), docenteId);
        CursoProgramacion curso = cursos.stream()
                .filter(item -> request.codeCourse().equals(item.getAsignatura().getCodeCourse()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "El docente no dictó esa asignatura en el período anterior"));
        EvaluacionDocente evaluacion = evaluacionDocenteRepository
                .findByDocenteIdAndAsignaturaCodeCourseAndPeriodoId(
                        docenteId, request.codeCourse(), anterior.getId())
                .orElseGet(() -> EvaluacionDocente.builder()
                        .docente(docente)
                        .asignatura(curso.getAsignatura())
                        .periodo(anterior)
                        .build());
        evaluacion.setCalificacion(request.calificacion());
        evaluacionDocenteRepository.save(evaluacion);

        try {
            docenteAsignaturaService.sincronizarDesdeCurso(curso.getId());
        } catch (Exception ex) {
            log.warn("HU-049: no se pudo sincronizar el histórico para el curso {}", curso.getId(), ex);
        }

        return new EvaluacionDocenteDto.Resumen(
                docenteId, anterior.getId(), anterior.getNombre(),
                request.codeCourse(), request.calificacion());
    }

    private PeriodoAcademico buscarPeriodoAnterior(Long periodoId) {
        PeriodoAcademico actual = periodoAcademicoRepository.findById(periodoId)
                .orElseThrow(() -> new BadRequestException("El período académico no existe"));
        return periodoAcademicoRepository.findAllByOrderByFechaInicioSemestreDesc()
                .stream()
                .filter(p -> p.getFechaInicioSemestre() != null
                        && p.getFechaInicioSemestre().isBefore(actual.getFechaInicioSemestre()))
                .findFirst()
                .orElse(null);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private Docente buscarPerfilPropio(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new ResourceNotFoundException(MSG_PERFIL_NO_ENCONTRADO);
        }
        return docenteRepository
                .findByCorreoInstitucionalAndEstadoTrue(correo.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException(MSG_PERFIL_NO_ENCONTRADO));
    }

    /** null o solo espacios se guardan como null; el resto, sin espacios en los extremos. */
    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private Facultad buscarFacultad(Integer idFacultad) {
        return facultadRepository.findById(idFacultad)
                .orElseThrow(() -> new BadRequestException(
                        "La facultad de adscripción no existe: " + idFacultad));
    }

    private Programa buscarPrograma(String codeProgram) {
        return programaRepository.findById(codeProgram)
                .orElseThrow(() -> new BadRequestException(
                        "El programa de adscripción no existe: " + codeProgram));
    }

    private DocenteDto.Response toResponse(Docente d) {
        Integer idFacultad = d.getFacultad() != null
                ? d.getFacultad().getId()
                : (d.getPrograma() != null ? d.getPrograma().getIdFacultadPrograma() : null);
        String nombreFacultad = d.getFacultad() != null ? d.getFacultad().getNombre() : null;

        return new DocenteDto.Response(
                d.getId(),
                d.getCodigo(),
                d.getNombres(),
                d.getApellidos(),
                d.getTipoDocumento(),
                d.getNumeroDocumento(),
                d.getCorreoInstitucional(),
                d.getCorreoPersonal(),
                d.getTelefono(),
                d.getTipoDocente(),
                d.getCategoria(),
                idFacultad,
                nombreFacultad,
                d.getPrograma() != null ? d.getPrograma().getCodeProgram() : null,
                d.getPrograma() != null ? d.getPrograma().getNameProgram() : null,
                d.getFotoUrl(),
                false,
                null,
                null,
                d.getEstado(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                d.getCelular());
    }

    private EvaluacionDocenteDto toEvaluacionResponse(EvaluacionDocente evaluacion) {
        return new EvaluacionDocenteDto(
                evaluacion.getDocente().getId(),
                evaluacion.getAsignatura().getCodeCourse(),
                evaluacion.getPeriodo().getId(),
                evaluacion.getPeriodo().getNombre(),
                evaluacion.getCalificacion());
    }
}
