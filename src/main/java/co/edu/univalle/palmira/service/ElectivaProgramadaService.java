package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.config.CuposProperties;
import co.edu.univalle.palmira.dto.ElectivaProgramadaDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Asignatura;
import co.edu.univalle.palmira.model.entity.CohortePeriodo;
import co.edu.univalle.palmira.model.entity.CursoProgramacion;
import co.edu.univalle.palmira.model.entity.Escuela;
import co.edu.univalle.palmira.model.entity.PensumAcademico;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.repository.AsignaturaRepository;
import co.edu.univalle.palmira.repository.CohortePeriodoRepository;
import co.edu.univalle.palmira.repository.CursoProgramacionRepository;
import co.edu.univalle.palmira.repository.EscuelaRepository;
import co.edu.univalle.palmira.repository.PensumAcademicoRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    /** Escuelas «todas las facultades»: sus electivas las puede programar cualquier programa. */
    static final Set<String> ESCUELAS_COMUNES = Set.of("990", "999");
    /** La complementaria solo la programa el coordinador cuando su espacio está en este semestre. */
    static final int SEMESTRE_COMPLEMENTARIA = 1;
    /** Marca de las complementarias que propone el coordinador: no ocupan un espacio de la malla. */
    public static final String ESPACIO_PROPUESTA = "COMPLEMENTARIA";
    /** Las propuestas no tienen semestre de malla. */
    public static final int SEMESTRE_PROPUESTA = 0;

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
    public static final String MSG_COMPLEMENTARIA_REPETIDA =
            "Esta electiva complementaria ya está programada en el período";
    public static final String MSG_JORNADA = "Debe indicar la jornada (diurna o nocturna)";
    public static final String MSG_NO_COMPLEMENTARIA =
            "La asignatura no es una electiva complementaria";
    public static final String MSG_COMPLEMENTARIA_SEMESTRE =
            "La electiva complementaria solo la programa el coordinador en el primer semestre";
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
    private final EscuelaRepository escuelaRepository;

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public ElectivaProgramadaDto.ListaResponse listar(String codeProgram) {
        String programa = texto(codeProgram, MSG_DATOS);
        AccesoProgramaService.Alcance alcance = validarAlcance(programa);
        List<ElectivaProgramadaDto.EscuelaItem> escuelas = escuelaRepository.findByPrograma(programa).stream()
                .map(e -> new ElectivaProgramadaDto.EscuelaItem(e.getCodigo(), e.getNombre()))
                .toList();
        PeriodoAcademico periodo = periodoVigente().orElse(null);
        if (periodo == null) {
            return new ElectivaProgramadaDto.ListaResponse(null, false, alcance.admin(), List.of(), escuelas,
                    List.of(), List.of(), List.of());
        }
        boolean plazoAbierto = ProgramacionService.plazoCoordinadorAbierto(periodo);
        List<PensumAcademico> pensumVigente = pensumRepository.findByIdCodeProgramAndVigenteTrue(programa);
        Map<String, String> nombres = nombresEspacios(pensumVigente);
        List<CursoProgramacion> programadas = cursoRepository.findElectivasProgramadas(periodo.getId(), programa);
        List<ElectivaProgramadaDto.Response> electivas = programadas.stream()
                .filter(c -> !esPropuesta(c.getCodeCourseEspacio()))
                .map(c -> respuesta(c, nombres))
                .toList();
        List<ElectivaProgramadaDto.Response> complementarias = programadas.stream()
                .filter(c -> esPropuesta(c.getCodeCourseEspacio()))
                .map(c -> respuesta(c, nombres))
                .toList();
        List<ElectivaProgramadaDto.PendienteItem> pendientes =
                pendientesObligatorias(periodo, programa, pensumVigente, nombres, electivas);
        return new ElectivaProgramadaDto.ListaResponse(periodo.getId(), plazoAbierto,
                alcance.admin() || plazoAbierto, electivas, escuelas, complementarias,
                bancoComplementarias(periodo.getId()), pendientes);
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
        if (esPropuesta(codeEspacio)) {
            return crearPropuesta(codeProgram, codeCourse, request);
        }

        AccesoProgramaService.Alcance alcance = validarAlcance(codeProgram);
        PeriodoAcademico periodo = periodoVigente()
                .orElseThrow(() -> new BadRequestException(MSG_SIN_PERIODO));
        exigirPlazo(alcance, periodo);

        Programa programa = programaRepository.findById(codeProgram)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_PROGRAMA));
        PensumAcademico espacio = buscarEspacio(codeProgram, codeEspacio);
        CohortePeriodo cohorte = buscarCohorte(periodo, programa, espacio.getSemestre());
        Asignatura asignatura = buscarElectiva(codeCourse, programa, espacio.getId().getCodeCourse());

        boolean ocupado = cursoRepository.findElectivasProgramadas(periodo.getId(), codeProgram).stream()
                .anyMatch(c -> espacio.getId().getCodeCourse().equalsIgnoreCase(c.getCodeCourseEspacio()));
        if (ocupado) {
            throw new ConflictException(MSG_ESPACIO_OCUPADO);
        }
        exigirNoRepetida(periodo.getId(), asignatura, codeProgram, cohorte.getJornada(), null, false);

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

    /**
     * Complementaria que propone el coordinador: no ocupa un espacio de la malla ni tiene semestre
     * (SEMESTRE_PROPUESTA), y se programa una sola vez en todo el período. La jornada la elige quien propone.
     */
    private ElectivaProgramadaDto.Response crearPropuesta(String codeProgram, String codeCourse,
                                                          ElectivaProgramadaDto.CrearRequest request) {
        AccesoProgramaService.Alcance alcance = validarAlcance(codeProgram);
        PeriodoAcademico periodo = periodoVigente()
                .orElseThrow(() -> new BadRequestException(MSG_SIN_PERIODO));
        exigirPlazo(alcance, periodo);

        Programa programa = programaRepository.findById(codeProgram)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_PROGRAMA));
        Asignatura asignatura = buscarElectiva(codeCourse, programa, ESPACIO_PROPUESTA);
        Jornada jornada = jornadaDePropuesta(request.jornada());
        exigirNoRepetida(periodo.getId(), asignatura, codeProgram, jornada, null, true);

        CursoProgramacion curso = CursoProgramacion.builder()
                .periodo(periodo)
                .programa(programa)
                .asignatura(asignatura)
                .semestre(SEMESTRE_PROPUESTA)
                .jornada(jornada)
                .numeroResolucion(resolucionDe(periodo, programa, jornada))
                .credits(asignatura.getCredits() != null ? asignatura.getCredits() : 0)
                .nombreAsignatura(asignatura.getNameCourse())
                .cupos(request.cupos())
                .estado(CursoProgramacion.ESTADO_PROGRAMADO)
                .codeCourseEspacio(ESPACIO_PROPUESTA)
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
            Asignatura nueva = buscarElectiva(request.codeCourse().trim(), curso.getPrograma(),
                    curso.getCodeCourseEspacio());
            exigirNoRepetida(curso.getPeriodo().getId(), nueva, codeProgram, curso.getJornada(), cursoId,
                    esPropuesta(curso.getCodeCourseEspacio()));
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

    /** Complementaria propuesta por el coordinador (sin espacio de malla). */
    static boolean esPropuesta(String codeEspacio) {
        return codeEspacio != null && codeEspacio.trim().equalsIgnoreCase(ESPACIO_PROPUESTA);
    }

    /** Espacio de la electiva complementaria (básicas): solo se programa si está en el primer semestre. */
    static boolean esEspacioComplementaria(String codeCourse) {
        return codeCourse != null && codeCourse.trim().equalsIgnoreCase(ESPACIO_COMPLEMENTARIA);
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
        if (!alcance.admin()
                && !alcance.codeProgram().equalsIgnoreCase(codeProgram)
                && !accesoProgramaService.tieneAccesoAPrograma(codeProgram)) {
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
        PensumAcademico espacio = pensumRepository.findByIdCodeProgramAndVigenteTrue(codeProgram).stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .filter(p -> esEspacio(p.getId().getCodeCourse())
                        || esEspacioComplementaria(p.getId().getCodeCourse()))
                .filter(p -> p.getId().getCodeCourse().equalsIgnoreCase(codeEspacio))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(MSG_ESPACIO));
        if (esEspacioComplementaria(codeEspacio)
                && !Objects.equals(espacio.getSemestre(), SEMESTRE_COMPLEMENTARIA)) {
            throw new BadRequestException(MSG_COMPLEMENTARIA_SEMESTRE);
        }
        return espacio;
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

    /**
     * Espacio de complementaria: cualquier electiva complementaria activa. Espacio de profesional: electiva
     * activa, no complementaria ni común, del banco del programa (ver {@link #delBancoDelPrograma}).
     */
    private Asignatura buscarElectiva(String codeCourse, Programa programa, String codeEspacio) {
        Asignatura asignatura = asignaturaRepository.findById(codeCourse)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_ASIGNATURA));
        if (!Boolean.TRUE.equals(asignatura.getActivo())) {
            throw new BadRequestException(MSG_ASIGNATURA_INACTIVA);
        }
        if (esEspacioComplementaria(codeEspacio) || esPropuesta(codeEspacio)) {
            if (!Boolean.TRUE.equals(asignatura.getIsElective())
                    || !Boolean.TRUE.equals(asignatura.getEsComplementaria())) {
                throw new BadRequestException(MSG_NO_COMPLEMENTARIA);
            }
            return asignatura;
        }
        boolean comun = Boolean.TRUE.equals(asignatura.getEsComplementaria())
                || Objects.equals(asignatura.getIdFacultad(), FACULTAD_COMUN);
        if (!Boolean.TRUE.equals(asignatura.getIsElective()) || comun || !delBancoDelPrograma(asignatura, programa)) {
            throw new BadRequestException(MSG_NO_PROFESIONAL);
        }
        return asignatura;
    }

    /**
     * Un programa con escuela ve las electivas cuyo código empieza por el código de alguna de sus escuelas
     * (o por 990/999, «todas las facultades»); sin escuela, las de su facultad.
     */
    private boolean delBancoDelPrograma(Asignatura asignatura, Programa programa) {
        List<String> escuelas = escuelaRepository.findByPrograma(programa.getCodeProgram()).stream()
                .map(Escuela::getCodigo)
                .toList();
        if (escuelas.isEmpty()) {
            return programa.getIdFacultadPrograma() != null
                    && Objects.equals(asignatura.getIdFacultad(), programa.getIdFacultadPrograma());
        }
        String codigo = asignatura.getCodeCourse() == null ? "" : asignatura.getCodeCourse().trim();
        String prefijo = codigo.length() >= 3 ? codigo.substring(0, 3) : codigo;
        return escuelas.contains(prefijo) || ESCUELAS_COMUNES.contains(prefijo);
    }

    /** La misma electiva no se repite en el programa y la jornada (misma clave que la programación). */
    private void exigirNoRepetida(Long periodoId, Asignatura asignatura, String codeProgram,
                                  Jornada jornada, Long cursoIgnorado, boolean propuesta) {
        if (propuesta && Boolean.TRUE.equals(asignatura.getEsComplementaria())) {
            // La complementaria general se propone una sola vez en todo el período (cualquier programa y jornada).
            // La del semestre 1 de la malla no cuenta: cada programa la programa en su espacio.
            if (complementariaProgramada(periodoId, asignatura.getCodeCourse(), cursoIgnorado)) {
                throw new ConflictException(MSG_COMPLEMENTARIA_REPETIDA);
            }
            return;
        }
        boolean repetida = cursoRepository.findByPeriodoAndAsignatura(periodoId, asignatura.getCodeCourse()).stream()
                .filter(c -> !c.isExcluido())
                .filter(c -> cursoIgnorado == null || !cursoIgnorado.equals(c.getId()))
                .anyMatch(c -> c.getPrograma().getCodeProgram().equalsIgnoreCase(codeProgram)
                        && c.getJornada() == jornada);
        if (repetida) {
            throw new ConflictException(MSG_REPETIDA);
        }
    }

    private boolean complementariaProgramada(Long periodoId, String codeCourse, Long cursoIgnorado) {
        return cursoRepository.findByPeriodoAndAsignatura(periodoId, codeCourse).stream()
                .filter(c -> !c.isExcluido())
                .filter(c -> esPropuesta(c.getCodeCourseEspacio()))
                .anyMatch(c -> cursoIgnorado == null || !cursoIgnorado.equals(c.getId()));
    }

    /** Complementarias activas; las ya propuestas (generales) en el período llevan el programa que las propuso. */
    private List<ElectivaProgramadaDto.AsignaturaItem> bancoComplementarias(Long periodoId) {
        return asignaturaRepository.findAll().stream()
                .filter(a -> Boolean.TRUE.equals(a.getActivo()) && Boolean.TRUE.equals(a.getIsElective())
                        && Boolean.TRUE.equals(a.getEsComplementaria()))
                .sorted(java.util.Comparator.comparing(Asignatura::getNameCourse,
                        java.util.Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(a -> new ElectivaProgramadaDto.AsignaturaItem(a.getCodeCourse(), a.getNameCourse(),
                        a.getCredits(), programaQueLaTiene(periodoId, a.getCodeCourse())))
                .toList();
    }

    /** Nombre del programa que ya programó la complementaria en el período; null si nadie la ha programado. */
    private String programaQueLaTiene(Long periodoId, String codeCourse) {
        return cursoRepository.findByPeriodoAndAsignatura(periodoId, codeCourse).stream()
                .filter(c -> !c.isExcluido())
                .filter(c -> esPropuesta(c.getCodeCourseEspacio()))
                .findFirst()
                .map(c -> c.getPrograma().getNameProgram() != null
                        ? c.getPrograma().getNameProgram() : c.getPrograma().getCodeProgram())
                .orElse(null);
    }

    /**
     * La complementaria no se programa en la jornada del programa: la elige quien la propone
     * (diurna o nocturna), sin valor por defecto.
     */
    private static Jornada jornadaDePropuesta(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new BadRequestException(MSG_JORNADA);
        }
        try {
            Jornada jornada = Jornada.valueOf(texto.trim().toUpperCase());
            if (jornada == Jornada.AMBAS) {
                throw new BadRequestException(MSG_JORNADA);
            }
            return jornada;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(MSG_JORNADA);
        }
    }

    /** Resolución de la cohorte confirmada del programa (la de la misma jornada si existe); N/A si no hay. */
    private String resolucionDe(PeriodoAcademico periodo, Programa programa, Jornada jornada) {
        List<CohortePeriodo> cohortes = cohorteRepository
                .findByPeriodoIdAndCodeProgram(periodo.getId(), programa.getCodeProgram()).stream()
                .filter(c -> Boolean.TRUE.equals(c.getConfirmada()) && c.getNumeroResolucion() != null)
                .toList();
        return cohortes.stream()
                .filter(c -> c.getJornada() == jornada)
                .findFirst()
                .or(() -> cohortes.stream().findFirst())
                .map(CohortePeriodo::getNumeroResolucion)
                .orElse("N/A");
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
        return nombresEspacios(pensumRepository.findByIdCodeProgramAndVigenteTrue(codeProgram));
    }

    private Map<String, String> nombresEspacios(List<PensumAcademico> pensumVigente) {
        Map<String, String> nombres = new HashMap<>();
        for (PensumAcademico p : pensumVigente) {
            if (esEspacio(p.getId().getCodeCourse()) || esEspacioComplementaria(p.getId().getCodeCourse())) {
                String porDefecto = esEspacioComplementaria(p.getId().getCodeCourse())
                        ? "Electiva Complementaria" : "Electiva Profesional";
                String nombre = p.getNameCourseSnapshot() != null && !p.getNameCourseSnapshot().isBlank()
                        ? p.getNameCourseSnapshot()
                        : porDefecto;
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

    /**
     * Calcula los espacios de electiva de la malla que pertenecen a semestres con cohorte confirmada
     * en el período vigente y que el coordinador todavía no ha escogido. Al cambiar de período vigente,
     * las electivas programadas del período anterior dejan de contar y los espacios se reinician.
     */
    private List<ElectivaProgramadaDto.PendienteItem> pendientesObligatorias(
            PeriodoAcademico periodo,
            String codeProgram,
            List<PensumAcademico> pensumVigente,
            Map<String, String> nombres,
            List<ElectivaProgramadaDto.Response> electivas) {
        Set<Integer> semestresConfirmados = cohorteRepository
                .findByPeriodoIdAndCodeProgram(periodo.getId(), codeProgram).stream()
                .filter(c -> Boolean.TRUE.equals(c.getConfirmada()) && c.getSemestre() != null)
                .map(CohortePeriodo::getSemestre)
                .collect(Collectors.toSet());
        if (semestresConfirmados.isEmpty()) {
            return List.of();
        }
        Set<String> espaciosOcupados = electivas.stream()
                .map(ElectivaProgramadaDto.Response::codeEspacio)
                .filter(Objects::nonNull)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        return pensumVigente.stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .filter(p -> p.getSemestre() != null && semestresConfirmados.contains(p.getSemestre()))
                .filter(p -> {
                    String cod = p.getId().getCodeCourse();
                    return esEspacio(cod)
                            || (esEspacioComplementaria(cod) && Objects.equals(p.getSemestre(), SEMESTRE_COMPLEMENTARIA));
                })
                .filter(p -> !espaciosOcupados.contains(p.getId().getCodeCourse().toUpperCase()))
                .sorted(Comparator.comparing(PensumAcademico::getSemestre)
                        .thenComparing(p -> p.getId().getCodeCourse()))
                .map(p -> {
                    String cod = p.getId().getCodeCourse();
                    boolean esComp = esEspacioComplementaria(cod);
                    String nombre = nombres.getOrDefault(cod.toUpperCase(),
                            esComp ? "Electiva Complementaria" : "Electiva Profesional");
                    return new ElectivaProgramadaDto.PendienteItem(cod, nombre, p.getSemestre(), esComp);
                })
                .toList();
    }

    private ElectivaProgramadaDto.Response respuesta(CursoProgramacion c, Map<String, String> nombres) {
        String espacio = c.getCodeCourseEspacio();
        boolean esComplementaria = esPropuesta(espacio) || esEspacioComplementaria(espacio);
        // En complementarias los docentes suelen pertenecer a otra facultad/escuela (artes, deporte, idiomas),
        // por eso no se restringe la búsqueda a la facultad del programa que la propone.
        Integer idFacultadFiltro = esComplementaria ? null : c.getPrograma().getIdFacultadPrograma();
        int asociados = docenteAsignaturaService.listar(null, c.getAsignatura().getCodeCourse(),
                idFacultadFiltro, true).size();
        String docente = c.getDocente() == null ? null
                : (c.getDocente().getNombres() + " " + c.getDocente().getApellidos()).trim();
        return new ElectivaProgramadaDto.Response(
                c.getId(),
                c.getPrograma().getCodeProgram(),
                espacio,
                espacio == null ? null : (esPropuesta(espacio) ? "Electiva complementaria"
                        : nombres.getOrDefault(espacio.toUpperCase(), "Electiva Profesional")),
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
                asociados == 0 ? ALERTA_SIN_DOCENTES : null,
                c.getAsignatura().getHorasSemanales(),
                c.getDia(),
                c.getHoraInicio(),
                c.getHoraFin(),
                c.getDia2(),
                c.getHoraInicio2(),
                c.getHoraFin2(),
                c.getHorario());
    }
}