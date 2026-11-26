package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.constant.AuthConstants;
import co.edu.univalle.palmira.dto.DocenteDto;
import co.edu.univalle.palmira.dto.EvaluacionDocenteDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.CursoProgramacion;
import co.edu.univalle.palmira.model.entity.Escuela;
import co.edu.univalle.palmira.model.entity.Facultad;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.entity.EvaluacionDocente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.repository.EscuelaRepository;
import co.edu.univalle.palmira.repository.EvaluacionDocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import co.edu.univalle.palmira.repository.CursoProgramacionRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.FacultadRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
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
    private final UsuarioAccesoService usuarioAccesoService;
    private final EscuelaRepository escuelaRepository;

    @Autowired
    public DocenteService(DocenteRepository docenteRepository,
                          ProgramaRepository programaRepository,
                          FacultadRepository facultadRepository,
                          EvaluacionDocenteRepository evaluacionDocenteRepository,
                          PeriodoAcademicoRepository periodoAcademicoRepository,
                          CursoProgramacionRepository cursoProgramacionRepository,
                          DocenteAsignaturaService docenteAsignaturaService,
                          UsuarioAccesoService usuarioAccesoService,
                          EscuelaRepository escuelaRepository) {
        this.docenteRepository = docenteRepository;
        this.programaRepository = programaRepository;
        this.facultadRepository = facultadRepository;
        this.evaluacionDocenteRepository = evaluacionDocenteRepository;
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.cursoProgramacionRepository = cursoProgramacionRepository;
        this.docenteAsignaturaService = docenteAsignaturaService;
        this.usuarioAccesoService = usuarioAccesoService;
        this.escuelaRepository = escuelaRepository;
    }

    DocenteService(DocenteRepository docenteRepository,
                   ProgramaRepository programaRepository,
                   FacultadRepository facultadRepository,
                   EvaluacionDocenteRepository evaluacionDocenteRepository,
                   PeriodoAcademicoRepository periodoAcademicoRepository,
                   CursoProgramacionRepository cursoProgramacionRepository,
                   DocenteAsignaturaService docenteAsignaturaService,
                   UsuarioAccesoService usuarioAccesoService) {
        this(docenteRepository, programaRepository, facultadRepository, evaluacionDocenteRepository,
                periodoAcademicoRepository, cursoProgramacionRepository, docenteAsignaturaService,
                usuarioAccesoService, null);
    }

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

        // Código (opcional) y correo duplicados: 409 sin id existente
        String codigo = normalizarOpcional(req.codigo());
        if (codigo != null && docenteRepository.existsByCodigo(codigo)) {
            throw new ConflictException(
                    "Ya existe un docente con el código " + codigo, null);
        }
        if (docenteRepository.existsByCorreoInstitucional(req.correoInstitucional())) {
            throw new ConflictException(
                    "Ya existe un docente con el correo " + req.correoInstitucional(), null);
        }

        Facultad facultad = req.idFacultad() != null ? buscarFacultad(req.idFacultad()) : null;

        Docente docente = Docente.builder()
                .codigo(codigo)
                .nombres(req.nombres().trim())
                .apellidos(req.apellidos().trim())
                .tipoDocumento(req.tipoDocumento())
                .numeroDocumento(req.numeroDocumento())
                .correoInstitucional(req.correoInstitucional().trim().toLowerCase())
                .telefono(normalizarOpcional(req.telefono()))
                .celular(normalizarOpcional(req.celular()))
                .direccion(normalizarOpcional(req.direccion()))
                .tipoDocente(req.tipoDocente())
                .categoria(req.categoria())
                .facultad(facultad)
                .build();

        Docente guardado = docenteRepository.save(docente);
        sincronizarUsuario(null, guardado);
        return toResponse(guardado);
    }

    // ---------------------------------------------------------------
    // Actualizar (flujo "ya existe, ¿desea actualizarla?")
    // Los campos null no se modifican.
    // ---------------------------------------------------------------
    @Transactional
    public DocenteDto.Response actualizar(Long id, DocenteDto.UpdateRequest req) {
        Docente docente = docenteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Docente no encontrado con id " + id));
        String correoAnterior = docente.getCorreoInstitucional();

        if (req.codigo() != null) {
            String nuevoCodigo = normalizarOpcional(req.codigo());
            if (nuevoCodigo != null && !nuevoCodigo.equals(docente.getCodigo())
                    && docenteRepository.existsByCodigo(nuevoCodigo)) {
                throw new ConflictException("Ya existe un docente con el código " + nuevoCodigo, null);
            }
            docente.setCodigo(nuevoCodigo);
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
            docente.setTelefono(normalizarOpcional(req.telefono()));
        if (req.celular() != null)
            docente.setCelular(normalizarOpcional(req.celular()));
        if (req.direccion() != null)
            docente.setDireccion(normalizarOpcional(req.direccion()));
        if (req.tipoDocente() != null)
            docente.setTipoDocente(req.tipoDocente());
        if (req.categoria() != null)
            docente.setCategoria(req.categoria());

        if (req.idFacultad() != null) {
            docente.setFacultad(buscarFacultad(req.idFacultad()));
        }

        Docente guardado = docenteRepository.save(docente);
        sincronizarUsuario(correoAnterior, guardado);
        return toResponse(guardado);
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
        Docente guardado = docenteRepository.save(docente);
        usuarioAccesoService.cambiarEstado(guardado.getCorreoInstitucional(), Boolean.TRUE.equals(guardado.getEstado()));
        return toResponse(guardado);
    }

    // ---------------------------------------------------------------
    // HU-022: Mi perfil (el correo sale siempre del JWT, nunca de la petición)
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public DocenteDto.Response obtenerPorCorreo(String correo) {
        return toResponse(buscarPerfilPropio(correo));
    }

    // ---------------------------------------------------------------
    // HU-015: Editar mis datos de contacto (correo personal, teléfono, celular y dirección) y facultad.
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
        String direccion = normalizarOpcional(req.direccion());

        Integer idFacultadActual = docente.getFacultad() != null ? docente.getFacultad().getId() : null;
        Integer nuevoIdFacultad = req.idFacultad();
        boolean cambioFacultad = nuevoIdFacultad != null && !Objects.equals(nuevoIdFacultad, idFacultadActual);

        Set<String> escuelasActuales = docente.getEscuelas() == null
                ? Set.of()
                : docente.getEscuelas().stream().map(Escuela::getCodigo).collect(Collectors.toSet());
        Set<String> nuevasEscuelasCodigos = req.codigosEscuelas() == null
                ? null
                : req.codigosEscuelas().stream()
                        .filter(Objects::nonNull)
                        .map(c -> c.trim().toUpperCase())
                        .filter(c -> !c.isBlank())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        boolean cambioEscuelas = nuevasEscuelasCodigos != null && !nuevasEscuelasCodigos.equals(escuelasActuales);

        boolean cambioContacto = !Objects.equals(correoPersonal, docente.getCorreoPersonal())
                || !Objects.equals(telefono, docente.getTelefono())
                || !Objects.equals(celular, docente.getCelular())
                || !Objects.equals(direccion, docente.getDireccion());

        if (!cambioContacto && !cambioFacultad && !cambioEscuelas) {
            throw new BadRequestException(MSG_SIN_CAMBIOS);
        }

        if (cambioFacultad) {
            docente.setFacultad(buscarFacultad(nuevoIdFacultad));
        }
        if (cambioEscuelas && escuelaRepository != null) {
            Set<Escuela> escuelasEntidades = new LinkedHashSet<>();
            for (String cod : nuevasEscuelasCodigos) {
                Escuela esc = escuelaRepository.findById(cod)
                        .orElseThrow(() -> new BadRequestException("La escuela no existe: " + cod));
                escuelasEntidades.add(esc);
            }
            docente.setEscuelas(escuelasEntidades);
        }
        docente.setCorreoPersonal(correoPersonal);
        docente.setTelefono(telefono);
        docente.setCelular(celular);
        docente.setDireccion(direccion);
        Docente guardado = docenteRepository.save(docente);
        sincronizarUsuario(null, guardado);
        return toResponse(guardado);
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
     * Devuelve la última evaluación registrada de cada docente para la asignatura,
     * en cualquier período anterior al período activo.
     */
    @Transactional(readOnly = true)
    public List<EvaluacionDocenteDto> listarEvaluacionesAnteriores(Long periodoId, String codeCourse) {
        PeriodoAcademico actual = periodoAcademicoRepository.findById(periodoId)
                .orElseThrow(() -> new BadRequestException("El período académico no existe"));

        List<EvaluacionDocente> evaluaciones = actual.getFechaInicioSemestre() != null
                ? evaluacionDocenteRepository.findAllByAsignaturaBeforePeriodo(codeCourse, actual.getFechaInicioSemestre())
                : List.of();
        if (evaluaciones.isEmpty()) {
            evaluaciones = evaluacionDocenteRepository
                    .findAllByPeriodoIdAndAsignaturaCodeCourse(periodoId, codeCourse);
        }

        // La consulta viene ordenada de más reciente a más antigua; la primera por docente es la vigente.
        Map<Long, EvaluacionDocente> ultimaPorDocente = new LinkedHashMap<>();
        for (EvaluacionDocente evaluacion : evaluaciones) {
            ultimaPorDocente.putIfAbsent(evaluacion.getDocente().getId(), evaluacion);
        }
        return ultimaPorDocente.values().stream()
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
        Map<String, EvaluacionDocente> evaluacionesPorCurso = evaluacionDocenteRepository
                .findAllByDocenteIdAndPeriodoId(docenteId, anterior.getId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        ev -> ev.getAsignatura().getCodeCourse(),
                        ev -> ev,
                        (first, ignored) -> first));

        return cursoProgramacionRepository.findByPeriodoIdAndDocenteId(anterior.getId(), docenteId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        curso -> curso.getAsignatura().getCodeCourse(),
                        curso -> curso,
                        (first, ignored) -> first))
                .values()
                .stream()
                .map(curso -> {
                    EvaluacionDocente evaluacion = evaluacionesPorCurso.get(curso.getAsignatura().getCodeCourse());
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
            log.warn("No se pudo sincronizar el histórico para el curso {}", curso.getId(), ex);
        }

        return new EvaluacionDocenteDto.Resumen(
                docenteId, anterior.getId(), anterior.getNombre(),
                request.codeCourse(), request.calificacion());
    }

    private PeriodoAcademico buscarPeriodoAnterior(Long periodoId) {
        PeriodoAcademico actual = periodoAcademicoRepository.findById(periodoId)
                .orElseThrow(() -> new BadRequestException("El período académico no existe"));
        if (actual.getFechaInicioSemestre() == null) {
            return null;
        }
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
    /** Refleja el docente en la tabla usuarios (rol docente) para que aparezca entre los usuarios de la app. */
    private void sincronizarUsuario(String correoAnterior, Docente d) {
        usuarioAccesoService.sincronizar(correoAnterior, d.getNombres(), d.getApellidos(),
                d.getCorreoInstitucional(), d.getCorreoPersonal(), d.getCelular(),
                AuthConstants.ROL_DOCENTE, !Boolean.FALSE.equals(d.getEstado()));
    }

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
        Integer idFacultad = d.getFacultad() != null ? d.getFacultad().getId() : null;
        String nombreFacultad = d.getFacultad() != null ? d.getFacultad().getNombre() : null;
        List<DocenteDto.EscuelaItem> escuelas = d.getEscuelas() == null
                ? List.of()
                : d.getEscuelas().stream()
                        .sorted(Comparator.comparing(Escuela::getCodigo))
                        .map(e -> new DocenteDto.EscuelaItem(e.getCodigo(), e.getNombre()))
                        .toList();

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
                null,
                null,
                null,
                false,
                null,
                null,
                d.getEstado(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                d.getCelular(),
                d.getDireccion(),
                escuelas);
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
