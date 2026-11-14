package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.config.CuposProperties;
import co.edu.univalle.palmira.dto.ElectivaProgramadaDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Asignatura;
import co.edu.univalle.palmira.model.entity.CohortePeriodo;
import co.edu.univalle.palmira.model.entity.CursoProgramacion;
import co.edu.univalle.palmira.model.entity.PensumAcademico;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.repository.AsignaturaRepository;
import co.edu.univalle.palmira.repository.CohortePeriodoRepository;
import co.edu.univalle.palmira.repository.CursoProgramacionRepository;
import co.edu.univalle.palmira.repository.PensumAcademicoRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Electivas profesionales: el coordinador escoge una electiva del banco de su programa y la
 * programa en un espacio «Electiva Profesional N» de la malla. La electiva es un curso más del
 * período vigente (docente y horario vacíos) vinculado al espacio por code_course_espacio.
 * La malla no se modifica; al pasar el período a historial el espacio vuelve a verse vacío.
 * Las notificaciones a los docentes (HU-038) quedan pendientes: aún no hay envío de correo.
 */
@Service
@RequiredArgsConstructor
public class ElectivaProgramadaService {

    public static final String PREFIJO_ESPACIO = "ELE-PROF-";
    public static final String ESPACIO_COMPLEMENTARIA = "ELE-PROF-COMPLEMENTARIA";
    /** Facultad de las asignaturas comunes: no son electivas profesionales. */
    static final int FACULTAD_COMUN = 2;

    public static final String MSG_FUERA_DE_PROGRAMA = "Solo puede programar electivas de su programa";
    public static final String MSG_DATOS = "Debe indicar el programa, el espacio de la malla, la electiva y los cupos";
    public static final String MSG_SIN_PERIODO = "No hay un período académico vigente";
    public static final String MSG_PROGRAMA = "Programa no encontrado";
    public static final String MSG_ESPACIO = "El espacio de electiva no existe en la malla vigente del programa";
    public static final String MSG_SIN_COHORTE =
            "No hay una cohorte confirmada de semestre %d para este programa en el período vigente";
    public static final String MSG_ASIGNATURA = "Asignatura no encontrada";
    public static final String MSG_NO_PROFESIONAL =
            "La asignatura no es una electiva profesional del banco de este programa";
    public static final String MSG_ASIGNATURA_INACTIVA = "La asignatura está inactiva";
    public static final String MSG_ESPACIO_OCUPADO =
            "El espacio ya tiene una electiva programada; modifíquela o quítela";
    public static final String MSG_REPETIDA =
            "Esta electiva ya está programada para el programa en la misma jornada";
    public static final String MSG_CUPOS = "Debe ingresar un número de cupos válido";
    public static final String MSG_CUPOS_MAX = "El número de cupos no puede superar el máximo permitido (%d)";
    public static final String MSG_NO_ES_ELECTIVA = "La electiva programada no existe";
    public static final String MSG_CON_DOCENTE =
            "La electiva ya tiene docente asignado; no se puede modificar ni quitar";
    public static final String MSG_EXCLUIDA = "La electiva está excluida";
    public static final String MSG_SIN_CAMBIOS = "No hay cambios que guardar";
    public static final String ALERTA_SIN_DOCENTES =
            "No hay docentes asociados a esta electiva. El administrador debe registrar "
                    + "la disponibilidad de un docente en su nombre.";

    private final CursoProgramacionRepository cursoRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final ProgramaRepository programaRepository;
    private final PensumAcademicoRepository pensumRepository;
    private final CohortePeriodoRepository cohorteRepository;
    private final AsignaturaRepository asignaturaRepository;
    private final AccesoProgramaService accesoProgramaService;
    private final DocenteAsignaturaService docenteAsignaturaService;
    private final ProgramacionService programacionService;
    private final BitacoraService bitacoraService;
    private final CuposProperties cuposProperties;

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public ElectivaProgramadaDto.ListaResponse listar(String codeProgram) {
        String programa = texto(codeProgram, MSG_DATOS);
        AccesoProgramaService.Alcance alcance = validarAlcance(programa);
        PeriodoAcademico periodo = periodoVigente().orElse(null);
        if (periodo == null) {
            return new ElectivaProgramadaDto.ListaResponse(null, false, alcance.admin(), List.of());
        }
        boolean plazoAbierto = ProgramacionService.plazoCoordinadorAbierto(periodo);
        Map<String, String> nombres = nombresEspacios(programa);
        List<ElectivaProgramadaDto.Response> electivas = cursoRepository
                .findElectivasProgramadas(periodo.getId(), programa).stream()
                .map(c -> respuesta(c, nombres))
                .toList();
        return new ElectivaProgramadaDto.ListaResponse(periodo.getId(), plazoAbierto,
                alcance.admin() || plazoAbierto, electivas);
    }

    // ------------------------------------------------------------------ crear

    @Transactional
    public ElectivaProgramadaDto.Response crear(ElectivaProgramadaDto.CrearRequest request) {
        if (request == null) {
            throw new BadRequestException(MSG_DATOS);
        }
        String codeProgram = texto(request.codeProgram(), MSG_DATOS);
        String codeEspacio = texto(request.codeEspacio(), MSG_DATOS);
        String codeCourse = texto(request.codeCourse(), MSG_DATOS);
        validarCupos(request.cupos());

        AccesoProgramaService.Alcance alcance = validarAlcance(codeProgram);
        PeriodoAcademico periodo = periodoVigente()
                .orElseThrow(() -> new BadRequestException(MSG_SIN_PERIODO));
        exigirPlazo(alcance, periodo);

        Programa programa = programaRepository.findById(codeProgram)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_PROGRAMA));
        PensumAcademico espacio = buscarEspacio(codeProgram, codeEspacio);
        CohortePeriodo cohorte = buscarCohorte(periodo, programa, espacio.getSemestre());
        Asignatura asignatura = buscarElectiva(codeCourse, programa);

        boolean ocupado = cursoRepository.findElectivasProgramadas(periodo.getId(), codeProgram).stream()
                .anyMatch(c -> espacio.getId().getCodeCourse().equalsIgnoreCase(c.getCodeCourseEspacio()));
        if (ocupado) {
            throw new ConflictException(MSG_ESPACIO_OCUPADO);
        }
        exigirNoRepetida(periodo.getId(), asignatura.getCodeCourse(), codeProgram, cohorte.getJornada(), null);

        CursoProgramacion curso = CursoProgramacion.builder()
                .periodo(periodo)
                .programa(programa)
                .asignatura(asignatura)
                .semestre(espacio.getSemestre())
                .jornada(cohorte.getJornada())
                .numeroResolucion(cohorte.getNumeroResolucion())
                .credits(asignatura.getCredits() != null ? asignatura.getCredits() : 0)
                .nombreAsignatura(asignatura.getNameCourse())
                .cupos(request.cupos())
                .estado(CursoProgramacion.ESTADO_PROGRAMADO)
                .codeCourseEspacio(espacio.getId().getCodeCourse())
                .grupo(siguienteGrupo(periodo.getId(), asignatura.getCodeCourse()))
                .build();
        CursoProgramacion guardado = cursoRepository.save(curso);

        bitacoraService.registrar(AccionBitacora.CREAR, ProgramacionService.ENTIDAD_CURSO,
                String.valueOf(guardado.getId()), periodo.getId(), null, datos(guardado));

        return respuesta(guardado, nombresEspacios(codeProgram));
    }

    // ------------------------------------------------------------------ modificar

    @Transactional
    public ElectivaProgramadaDto.Response modificar(Long cursoId,
                                                    ElectivaProgramadaDto.ModificarRequest request) {
        if (request == null || (request.codeCourse() == null && request.cupos() == null)) {
            throw new BadRequestException(MSG_SIN_CAMBIOS);
        }
        CursoProgramacion curso = cursoEditable(cursoId);
        String codeProgram = curso.getPrograma().getCodeProgram();
        Map<String, Object> anterior = datos(curso);
        String codigoAnterior = curso.getAsignatura().getCodeCourse();

        if (request.cupos() != null) {
            validarCupos(request.cupos());
            curso.setCupos(request.cupos());
        }

        boolean cambiaAsignatura = request.codeCourse() != null && !request.codeCourse().isBlank()
                && !request.codeCourse().trim().equalsIgnoreCase(codigoAnterior);
        if (cambiaAsignatura) {
            Asignatura nueva = buscarElectiva(request.codeCourse().trim(), curso.getPrograma());
            exigirNoRepetida(curso.getPeriodo().getId(), nueva.getCodeCourse(), codeProgram,
                    curso.getJornada(), cursoId);
            curso.setGrupo(siguienteGrupo(curso.getPeriodo().getId(), nueva.getCodeCourse()));
            curso.setAsignatura(nueva);
            curso.setNombreAsignatura(nueva.getNameCourse());
            curso.setCredits(nueva.getCredits() != null ? nueva.getCredits() : 0);
        }

        CursoProgramacion guardado = cursoRepository.save(curso);
        bitacoraService.registrar(AccionBitacora.EDITAR, ProgramacionService.ENTIDAD_CURSO,
                String.valueOf(cursoId), curso.getPeriodo().getId(), anterior, datos(guardado));

        if (cambiaAsignatura) {
            cursoRepository.flush();
            programacionService.renumerarGrupos(curso.getPeriodo().getId(), codigoAnterior);
        }
        return respuesta(guardado, nombresEspacios(codeProgram));
    }

    // ------------------------------------------------------------------ quitar

    @Transactional
    public void quitar(Long cursoId) {
        CursoProgramacion curso = cursoEditable(cursoId);
        Long periodoId = curso.getPeriodo().getId();
        String codeCourse = curso.getAsignatura().getCodeCourse();
        Map<String, Object> anterior = datos(curso);

        cursoRepository.delete(curso);
        cursoRepository.flush();

        Map<String, Object> nuevo = new LinkedHashMap<>();
        nuevo.put("quitada", true);
        bitacoraService.registrar(AccionBitacora.DESACTIVAR, ProgramacionService.ENTIDAD_CURSO,
                String.valueOf(cursoId), periodoId, anterior, nuevo);

        programacionService.renumerarGrupos(periodoId, codeCourse);
    }

    // ------------------------------------------------------------------ apoyo

    /** Espacio de electiva profesional: ELE-PROF-I, II, ...; la complementaria (básicas) no cuenta. */
    static boolean esEspacio(String codeCourse) {
        if (codeCourse == null) {
            return false;
        }
        String codigo = codeCourse.trim().toUpperCase();
        return codigo.startsWith(PREFIJO_ESPACIO) && !codigo.equals(ESPACIO_COMPLEMENTARIA);
    }

    private CursoProgramacion cursoEditable(Long cursoId) {
        CursoProgramacion curso = cursoRepository.findByIdWithDetails(cursoId)
                .orElseThrow(() -> new ResourceNotFoundException(ProgramacionService.MSG_CURSO_NO_ENCONTRADO));
        if (curso.getCodeCourseEspacio() == null) {
            throw new ResourceNotFoundException(MSG_NO_ES_ELECTIVA);
        }
        AccesoProgramaService.Alcance alcance = validarAlcance(curso.getPrograma().getCodeProgram());
        exigirPlazo(alcance, curso.getPeriodo());
        if (curso.isExcluido()) {
            throw new ConflictException(MSG_EXCLUIDA);
        }
        if (curso.getDocente() != null) {
            throw new ConflictException(MSG_CON_DOCENTE);
        }
        return curso;
    }

    private AccesoProgramaService.Alcance validarAlcance(String codeProgram) {
        AccesoProgramaService.Alcance alcance = accesoProgramaService.alcanceActual();
        if (!alcance.admin() && !alcance.codeProgram().equalsIgnoreCase(codeProgram)) {
            throw new AccessDeniedException(MSG_FUERA_DE_PROGRAMA);
        }
        return alcance;
    }

    /** El administrador siempre puede; el coordinador respeta el plazo del período. */
    private void exigirPlazo(AccesoProgramaService.Alcance alcance, PeriodoAcademico periodo) {
        if (!alcance.admin() && !ProgramacionService.plazoCoordinadorAbierto(periodo)) {
            throw new ConflictException(ProgramacionService.MSG_PLAZO_CERRADO);
        }
    }

    private Optional<PeriodoAcademico> periodoVigente() {
        return periodoRepository.findFirstByVigenteTrue()
                .filter(p -> p.getEstado() == null || "VIGENTE".equalsIgnoreCase(p.getEstado()));
    }

    private PensumAcademico buscarEspacio(String codeProgram, String codeEspacio) {
        return pensumRepository.findByIdCodeProgramAndVigenteTrue(codeProgram).stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .filter(p -> esEspacio(p.getId().getCodeCourse()))
                .filter(p -> p.getId().getCodeCourse().equalsIgnoreCase(codeEspacio))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(MSG_ESPACIO));
    }

    private CohortePeriodo buscarCohorte(PeriodoAcademico periodo, Programa programa, Integer semestre) {
        List<CohortePeriodo> cohortes = cohorteRepository
                .findByPeriodoIdAndCodeProgram(periodo.getId(), programa.getCodeProgram()).stream()
                .filter(c -> Boolean.TRUE.equals(c.getConfirmada()) && Objects.equals(c.getSemestre(), semestre))
                .toList();
        if (cohortes.isEmpty()) {
            throw new ConflictException(String.format(MSG_SIN_COHORTE, semestre));
        }
        return cohortes.stream()
                .filter(c -> c.getJornada() == programa.getJornada())
                .findFirst()
                .orElse(cohortes.get(0));
    }

    /** Banco profesional: electiva activa, no complementaria, de la facultad del programa (no común). */
    private Asignatura buscarElectiva(String codeCourse, Programa programa) {
        Asignatura asignatura = asignaturaRepository.findById(codeCourse)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_ASIGNATURA));
        if (!Boolean.TRUE.equals(asignatura.getActivo())) {
            throw new BadRequestException(MSG_ASIGNATURA_INACTIVA);
        }
        boolean comun = Boolean.TRUE.equals(asignatura.getEsComplementaria())
                || Objects.equals(asignatura.getIdFacultad(), FACULTAD_COMUN);
        boolean delPrograma = programa.getIdFacultadPrograma() != null
                && Objects.equals(asignatura.getIdFacultad(), programa.getIdFacultadPrograma());
        if (!Boolean.TRUE.equals(asignatura.getIsElective()) || comun || !delPrograma) {
            throw new BadRequestException(MSG_NO_PROFESIONAL);
        }
        return asignatura;
    }

    /** La misma electiva no se repite en el programa y la jornada (misma clave que la programación). */
    private void exigirNoRepetida(Long periodoId, String codeCourse, String codeProgram,
                                  Jornada jornada, Long cursoIgnorado) {
        boolean repetida = cursoRepository.findByPeriodoAndAsignatura(periodoId, codeCourse).stream()
                .filter(c -> !c.isExcluido())
                .filter(c -> cursoIgnorado == null || !cursoIgnorado.equals(c.getId()))
                .anyMatch(c -> c.getPrograma().getCodeProgram().equalsIgnoreCase(codeProgram)
                        && c.getJornada() == jornada);
        if (repetida) {
            throw new ConflictException(MSG_REPETIDA);
        }
    }

    /** Siguiente grupo de la serie de la asignatura en el período (desde 50). */
    private int siguienteGrupo(Long periodoId, String codeCourse) {
        return cursoRepository.findByPeriodoAndAsignaturaForUpdate(periodoId, codeCourse).stream()
                .filter(c -> !c.isExcluido() && c.getGrupo() != null)
                .mapToInt(CursoProgramacion::getGrupo)
                .max()
                .orElse(CursoProgramacion.GRUPO_INICIAL - 1) + 1;
    }

    private void validarCupos(Integer cupos) {
        if (cupos == null || cupos <= 0) {
            throw new BadRequestException(MSG_CUPOS);
        }
        if (cupos > cuposProperties.getMaxPorGrupo()) {
            throw new BadRequestException(String.format(MSG_CUPOS_MAX, cuposProperties.getMaxPorGrupo()));
        }
    }

    private static String texto(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new BadRequestException(mensaje);
        }
        return valor.trim();
    }

    private Map<String, String> nombresEspacios(String codeProgram) {
        Map<String, String> nombres = new HashMap<>();
        for (PensumAcademico p : pensumRepository.findByIdCodeProgramAndVigenteTrue(codeProgram)) {
            if (esEspacio(p.getId().getCodeCourse())) {
                String nombre = p.getNameCourseSnapshot() != null && !p.getNameCourseSnapshot().isBlank()
                        ? p.getNameCourseSnapshot()
                        : "Electiva Profesional";
                nombres.put(p.getId().getCodeCourse().toUpperCase(), nombre);
            }
        }
        return nombres;
    }

    private Map<String, Object> datos(CursoProgramacion c) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("codigoCurso", c.getGrupo() == null ? null : c.getAsignatura().getCodeCourse() + "-" + c.getGrupo());
        datos.put("codeProgram", c.getPrograma().getCodeProgram());
        datos.put("espacio", c.getCodeCourseEspacio());
        datos.put("codeCourse", c.getAsignatura().getCodeCourse());
        datos.put("cupos", c.getCupos());
        datos.put("semestre", c.getSemestre());
        datos.put("jornada", String.valueOf(c.getJornada()));
        return datos;
    }

    private ElectivaProgramadaDto.Response respuesta(CursoProgramacion c, Map<String, String> nombres) {
        int asociados = docenteAsignaturaService.listar(null, c.getAsignatura().getCodeCourse(),
                c.getPrograma().getIdFacultadPrograma(), true).size();
        String docente = c.getDocente() == null ? null
                : (c.getDocente().getNombres() + " " + c.getDocente().getApellidos()).trim();
        String espacio = c.getCodeCourseEspacio();
        return new ElectivaProgramadaDto.Response(
                c.getId(),
                c.getPrograma().getCodeProgram(),
                espacio,
                espacio == null ? null : nombres.getOrDefault(espacio.toUpperCase(), "Electiva Profesional"),
                c.getSemestre(),
                c.getAsignatura().getCodeCourse(),
                c.getNombreAsignatura(),
                c.getGrupo(),
                c.getGrupo() == null ? null : c.getAsignatura().getCodeCourse() + "-" + c.getGrupo(),
                c.getCupos(),
                c.getJornada(),
                c.getDocente() == null ? null : c.getDocente().getId(),
                docente,
                asociados,
                asociados == 0,
                asociados == 0 ? ALERTA_SIN_DOCENTES : null);
    }
}