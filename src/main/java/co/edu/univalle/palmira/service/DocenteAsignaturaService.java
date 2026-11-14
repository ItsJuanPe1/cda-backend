package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.DocenteAsignaturaDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.*;
import co.edu.univalle.palmira.model.enums.OrigenAsociacion;
import co.edu.univalle.palmira.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * HU-049: asociar docentes a las asignaturas que han dictado.
 * El histórico no se borra físicamente: quitar una asociación la desactiva (RN-13).
 * La asociación queda ligada a la facultad de adscripción del docente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
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
    private final CursoProgramacionRepository cursoProgramacionRepository;
    private final EvaluacionDocenteRepository evaluacionDocenteRepository;

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int sincronizarDesdeHistorico() {
        List<CursoProgramacion> cursos = cursoProgramacionRepository.findHistorialDocenteAsignatura();
        List<EvaluacionDocente> evaluaciones = evaluacionDocenteRepository.findAllWithDetails();

        Map<ClaveAsociacion, Candidato> candidatos = new HashMap<>();
        Map<Integer, Facultad> facultades = new HashMap<>();
        Map<ClaveCursoPeriodo, CursoProgramacion> cursosExactos = new HashMap<>();
        Map<ClaveDocenteAsignatura, CursoProgramacion> cursosPorDocenteAsignatura = new HashMap<>();

        for (CursoProgramacion curso : cursos) {
            indexarCurso(curso, cursosExactos, cursosPorDocenteAsignatura);
            fusionar(candidatos, candidatoDeCurso(curso, facultades));
        }
        for (EvaluacionDocente evaluacion : evaluaciones) {
            fusionar(candidatos, candidatoDeEvaluacion(
                    evaluacion, cursosExactos, cursosPorDocenteAsignatura, facultades));
        }

        int creadas = crearAsociacionesFaltantes(candidatos);
        log.info("HU-049: sincronización del histórico completada; asociaciones nuevas: {}", creadas);
        return creadas;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean sincronizarDesdeCurso(Long cursoId) {
        if (cursoId == null) {
            return false;
        }
        return cursoProgramacionRepository.findByIdWithDetails(cursoId)
                .filter(curso -> !curso.isExcluido())
                .map(curso -> {
                    Candidato candidato = candidatoDeCurso(curso, new HashMap<>());
                    if (candidato == null) {
                        return false;
                    }
                    ClaveAsociacion clave = claveDe(candidato);
                    if (asociacionRepository.existsByDocenteIdAndAsignaturaCodeCourseAndFacultadId(
                            clave.docenteId(), clave.codeCourse(), clave.idFacultad())) {
                        return false;
                    }
                    asociacionRepository.save(nuevaAsociacion(candidato));
                    return true;
                })
                .orElse(false);
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

    private void indexarCurso(CursoProgramacion curso,
                              Map<ClaveCursoPeriodo, CursoProgramacion> exactos,
                              Map<ClaveDocenteAsignatura, CursoProgramacion> porDocenteAsignatura) {
        if (curso.getDocente() == null || curso.getAsignatura() == null || curso.getPeriodo() == null) {
            return;
        }
        String codeCourse = normalizarCodigo(curso.getAsignatura().getCodeCourse());
        exactos.putIfAbsent(
                new ClaveCursoPeriodo(curso.getDocente().getId(), codeCourse, curso.getPeriodo().getId()),
                curso);

        ClaveDocenteAsignatura simple = new ClaveDocenteAsignatura(
                curso.getDocente().getId(), codeCourse);
        CursoProgramacion actual = porDocenteAsignatura.get(simple);
        if (actual == null || esPeriodoMasReciente(curso.getPeriodo(), actual.getPeriodo())) {
            porDocenteAsignatura.put(simple, curso);
        }
    }

    private Candidato candidatoDeCurso(CursoProgramacion curso, Map<Integer, Facultad> facultades) {
        if (curso.getDocente() == null || curso.getAsignatura() == null) {
            return null;
        }
        Facultad facultad = resolverFacultadDeCurso(curso, facultades);
        if (facultad == null) {
            return null;
        }
        return new Candidato(curso.getDocente(), curso.getAsignatura(), facultad, curso.getPeriodo());
    }

    private Candidato candidatoDeEvaluacion(
            EvaluacionDocente evaluacion,
            Map<ClaveCursoPeriodo, CursoProgramacion> cursosExactos,
            Map<ClaveDocenteAsignatura, CursoProgramacion> cursosPorDocenteAsignatura,
            Map<Integer, Facultad> facultades) {
        if (evaluacion.getDocente() == null || evaluacion.getAsignatura() == null) {
            return null;
        }
        String codeCourse = normalizarCodigo(evaluacion.getAsignatura().getCodeCourse());
        CursoProgramacion curso = cursosExactos.get(new ClaveCursoPeriodo(
                evaluacion.getDocente().getId(), codeCourse, evaluacion.getPeriodo().getId()));
        if (curso == null) {
            curso = cursosPorDocenteAsignatura.get(new ClaveDocenteAsignatura(
                    evaluacion.getDocente().getId(), codeCourse));
        }

        Facultad facultad = curso != null ? resolverFacultadDeCurso(curso, facultades) : null;
        if (facultad == null) {
            facultad = buscarFacultad(evaluacion.getAsignatura().getIdFacultad(), facultades);
        }
        if (facultad == null) {
            facultad = resolverFacultadOpcional(evaluacion.getDocente(), facultades);
        }
        if (facultad == null) {
            return null;
        }
        return new Candidato(
                evaluacion.getDocente(), evaluacion.getAsignatura(), facultad, evaluacion.getPeriodo());
    }

    private void fusionar(Map<ClaveAsociacion, Candidato> candidatos, Candidato candidato) {
        if (candidato == null) {
            return;
        }
        candidatos.merge(claveDe(candidato), candidato,
                (actual, nuevo) -> esPeriodoMasReciente(nuevo.periodo(), actual.periodo())
                        ? nuevo
                        : actual);
    }

    private int crearAsociacionesFaltantes(Map<ClaveAsociacion, Candidato> candidatos) {
        if (candidatos.isEmpty()) {
            return 0;
        }
        Set<ClaveAsociacion> existentes = new HashSet<>();
        for (DocenteAsignatura asociacion : asociacionRepository.findAllWithDetails()) {
            existentes.add(claveDe(asociacion));
        }

        List<DocenteAsignatura> nuevas = new ArrayList<>();
        for (Map.Entry<ClaveAsociacion, Candidato> entry : candidatos.entrySet()) {
            if (existentes.add(entry.getKey())) {
                nuevas.add(nuevaAsociacion(entry.getValue()));
            }
        }
        if (nuevas.isEmpty()) {
            return 0;
        }
        asociacionRepository.saveAll(nuevas);
        return nuevas.size();
    }

    private DocenteAsignatura nuevaAsociacion(Candidato candidato) {
        return DocenteAsignatura.builder()
                .docente(candidato.docente())
                .asignatura(candidato.asignatura())
                .facultad(candidato.facultad())
                .periodo(candidato.periodo())
                .origen(OrigenAsociacion.PROGRAMACION)
                .activo(true)
                .desactivadoAt(null)
                .build();
    }

    private static ClaveAsociacion claveDe(Candidato candidato) {
        return claveDe(candidato.docente(), candidato.asignatura(), candidato.facultad());
    }

    private static ClaveAsociacion claveDe(DocenteAsignatura asociacion) {
        return claveDe(asociacion.getDocente(), asociacion.getAsignatura(), asociacion.getFacultad());
    }

    private static ClaveAsociacion claveDe(Docente docente, Asignatura asignatura, Facultad facultad) {
        return new ClaveAsociacion(
                docente.getId(),
                normalizarCodigo(asignatura.getCodeCourse()),
                facultad.getId());
    }

    private static boolean esPeriodoMasReciente(PeriodoAcademico candidato, PeriodoAcademico actual) {
        if (candidato == null) {
            return false;
        }
        if (actual == null) {
            return true;
        }
        LocalDate fechaCandidata = candidato.getFechaInicioSemestre();
        LocalDate fechaActual = actual.getFechaInicioSemestre();
        if (fechaCandidata == null) {
            return false;
        }
        if (fechaActual == null) {
            return true;
        }
        if (!fechaCandidata.equals(fechaActual)) {
            return fechaCandidata.isAfter(fechaActual);
        }
        return candidato.getId() != null && actual.getId() != null
                && candidato.getId() > actual.getId();
    }

    private Facultad resolverFacultadOpcional(Docente docente, Map<Integer, Facultad> facultades) {
        if (docente.getFacultad() != null) {
            return docente.getFacultad();
        }
        if (docente.getPrograma() != null) {
            return buscarFacultad(docente.getPrograma().getIdFacultadPrograma(), facultades);
        }
        return null;
    }

    private Facultad buscarFacultad(Integer idFacultad, Map<Integer, Facultad> facultades) {
        if (idFacultad == null) {
            return null;
        }
        if (facultades.containsKey(idFacultad)) {
            return facultades.get(idFacultad);
        }
        Facultad facultad = facultadRepository.findById(idFacultad).orElse(null);
        facultades.put(idFacultad, facultad);
        return facultad;
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
        return resolverFacultadDeCurso(curso, new HashMap<>());
    }

    private Facultad resolverFacultadDeCurso(CursoProgramacion curso, Map<Integer, Facultad> facultades) {
        Integer idFacultad = null;
        if (curso.getPrograma() != null) {
            idFacultad = curso.getPrograma().getIdFacultadPrograma();
        }
        if (idFacultad == null && curso.getAsignatura() != null) {
            idFacultad = curso.getAsignatura().getIdFacultad();
        }
        return buscarFacultad(idFacultad, facultades);
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

    private record ClaveAsociacion(Long docenteId, String codeCourse, Integer idFacultad) {
    }

    private record ClaveCursoPeriodo(Long docenteId, String codeCourse, Long periodoId) {
    }

    private record ClaveDocenteAsignatura(Long docenteId, String codeCourse) {
    }

    private record Candidato(Docente docente, Asignatura asignatura, Facultad facultad,
                             PeriodoAcademico periodo) {
    }

    private record Resultado(DocenteAsignatura asociacion, boolean reactivada) {
    }
}
