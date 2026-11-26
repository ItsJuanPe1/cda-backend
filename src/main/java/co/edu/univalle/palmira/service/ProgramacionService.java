package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.config.CuposProperties;
import co.edu.univalle.palmira.dto.ProgramacionDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.*;
import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.repository.*;
//import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio central para la lógica de programación académica de la sede.
 * <p>
 * Responsabilidades principales:
 * <ul>
 *   <li><b>Generación de cursos</b>: crea las ofertas a partir de cohortes confirmadas y la malla curricular (pensum).</li>
 *   <li><b>Gestión de cupos</b>: actualización de cupos por curso verificando plazos y topes permitidos.</li>
 *   <li><b>Ajustes y exclusiones</b>: exclusión y reactivación justificada de cursos auditada en bitácora,
 *       asegurando la renumeración ordenada de grupos (iniciando en 50).</li>
 *   <li><b>Asignación docente</b>: asignación de profesores validando pertenencia a su disponibilidad semanal,
 *       soporte para 1 día continuo o 2 días divididos, validación de cruces de horario y límite
 *       máximo de 20 horas de carga semanal.</li>
 * </ul>
 * </p>
 */
@Service
public class ProgramacionService {

    // HU-047: mensajes (la HU no los define; ver docs/contratos/HU-047.md)
    public static final String MSG_JUSTIFICACION_OBLIGATORIA = "La justificación es obligatoria";
    public static final String MSG_JUSTIFICACION_LARGA = "La justificación no puede superar 500 caracteres";
    public static final String MSG_CURSO_NO_ENCONTRADO = "Curso no encontrado";
    public static final String MSG_YA_EXCLUIDO = "El curso ya está excluido";
    public static final String MSG_NO_EXCLUIDO = "El curso no está excluido";

    // Carga docente
    public static final String MSG_ASIGNATURA_SIN_HORAS = "La asignatura no tiene horas semanales definidas; defínalas antes de asignar docente";
    public static final String MSG_CURSO_NO_PROGRAMADO = "La asignatura no está programada en este período para el programa";

    // HU-047 criterio 4: motivos de «Requiere revisión»
    public static final String REVISION_FUERA_DE_MALLA = "La asignatura ya no está en la malla del programa";
    public static final String REVISION_ASIGNATURA_INACTIVA = "La asignatura está inactiva en el catálogo";
    public static final String REVISION_SIN_COHORTE = "La cohorte ya no está confirmada en el periodo";

    // HU-059: mensajes
    public static final String MSG_CUPO_INVALIDO = "Debe ingresar un número de cupos válido";
    public static final String MSG_CUPO_EXCEDE_MAX = "El número de cupos no puede superar el máximo permitido (%d)";
    public static final String MSG_CUPO_EXCLUIDO = "No se pueden asignar cupos a un curso excluido";
    public static final String MSG_PLAZO_CERRADO = "El plazo de edición ya cerró";
    public static final String MSG_CUPOS_GUARDADOS = "Cupos de estudiantes registrados correctamente";
    public static final String MSG_CUPOS_SIN_DATOS = "No se recibieron cupos para actualizar";
    public static final String MSG_CURSO_OTRO_PERIODO = "El curso no pertenece al período indicado";

    static final int MAX_JUSTIFICACION = 500;
    static final String ENTIDAD_CURSO = "CURSO";

    private final CursoProgramacionRepository cursoRepository;
    private final CohortePeriodoRepository cohorteRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final ProgramaRepository programaRepository;
    private final PensumAcademicoRepository pensumRepository;
    private final DocenteAsignaturaService docenteAsignaturaService;
    private final BitacoraService bitacoraService;
    private final DocenteRepository docenteRepository;
    private final DisponibilidadDocenteRepository disponibilidadDocenteRepository;
    private final AsignaturaRepository asignaturaRepository;
    private final AccesoProgramaService accesoProgramaService;
    private final CargaDocenteService cargaDocenteService;
    private final CuposProperties cuposProperties;
    private final ProgramacionExcelService programacionExcelService;

    @Autowired
    public ProgramacionService(
            CursoProgramacionRepository cursoRepository,
            CohortePeriodoRepository cohorteRepository,
            PeriodoAcademicoRepository periodoRepository,
            ProgramaRepository programaRepository,
            PensumAcademicoRepository pensumRepository,
            DocenteAsignaturaService docenteAsignaturaService,
            BitacoraService bitacoraService,
            DocenteRepository docenteRepository,
            DisponibilidadDocenteRepository disponibilidadDocenteRepository,
            AsignaturaRepository asignaturaRepository,
            AccesoProgramaService accesoProgramaService,
            CargaDocenteService cargaDocenteService,
            CuposProperties cuposProperties,
            ProgramacionExcelService programacionExcelService) {
        this.cursoRepository = cursoRepository;
        this.cohorteRepository = cohorteRepository;
        this.periodoRepository = periodoRepository;
        this.programaRepository = programaRepository;
        this.pensumRepository = pensumRepository;
        this.docenteAsignaturaService = docenteAsignaturaService;
        this.bitacoraService = bitacoraService;
        this.docenteRepository = docenteRepository;
        this.disponibilidadDocenteRepository = disponibilidadDocenteRepository;
        this.asignaturaRepository = asignaturaRepository;
        this.accesoProgramaService = accesoProgramaService;
        this.cargaDocenteService = cargaDocenteService;
        this.cuposProperties = cuposProperties;
        this.programacionExcelService = programacionExcelService;
    }

    /** Curso que corresponde según las cohortes confirmadas y la malla. */
    private record CursoOfertado(CohortePeriodo cohorte, PensumAcademico pensum) {
        String clave() {
            return ProgramacionService.clave(cohorte.getPrograma().getCodeProgram(),
                    pensum.getAsignatura().getCodeCourse(), cohorte.getJornada());
        }
    }

    @Transactional
    public ProgramacionDto.ResumenResponse generarProgramacion(Long periodoId, boolean sobrescribir) {
        PeriodoAcademico periodo = periodoRepository.findById(periodoId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Período académico no encontrado con ID: " + periodoId));

        long conteoExistente = cursoRepository.countByPeriodoId(periodoId);
        // Las electivas programadas a mano por el coordinador no cuentan como
        // «programación generada».
        long conteoGenerado = conteoExistente
                - cursoRepository.countByPeriodoIdAndCodeCourseEspacioIsNotNull(periodoId);
        if (conteoGenerado > 0 && !sobrescribir) {
            throw new ConflictException("Ya existe una programación generada para este período con " + conteoGenerado
                    + " cursos. Requiere confirmación para regenerar.");
        }

        // 1. Validar que todos los programas activos tengan cohortes confirmadas
        List<Programa> programasActivos = programaRepository.findByEstadoOrderByNameProgramAsc(EstadoPrograma.ACTIVO);
        List<CohortePeriodo> cohortesConfirmadas = cohorteRepository.findByPeriodoIdAndConfirmadaTrue(periodoId);

        Set<String> programasConCohortes = cohortesConfirmadas.stream()
                .map(c -> c.getPrograma().getCodeProgram())
                .collect(Collectors.toSet());

        List<String> programasFaltantes = programasActivos.stream()
                .filter(p -> !programasConCohortes.contains(p.getCodeProgram()))
                .map(Programa::getNameProgram)
                .collect(Collectors.toList());

        if (!programasFaltantes.isEmpty()) {
            throw new BadRequestException(
                    "No se puede generar la programación: faltan cohortes confirmadas para los programas: " +
                            String.join(", ", programasFaltantes));
        }

        // 2. Cursos que corresponden según cohortes y mallas (valida que cada cohorte
        // tenga asignaturas)
        Map<String, CursoOfertado> oferta = calcularOferta(cohortesConfirmadas, true);

        // 3. HU-047: al regenerar no se borra nada (RN-13). Se concilia por
        // programa#asignatura#jornada:
        // los cursos que siguen conservan id, docente, estado y exclusión; los nuevos
        // se agregan.
        List<CursoProgramacion> existentes = conteoExistente > 0
                ? new ArrayList<>(cursoRepository.findByPeriodoIdWithDetails(periodoId))
                : new ArrayList<>();
        Map<String, CursoProgramacion> existentesPorClave = new HashMap<>();
        for (CursoProgramacion c : existentes) {
            if (c.getCodeCourseEspacio() == null) {
                existentesPorClave.putIfAbsent(clave(c), c);
            }
        }

        List<CursoProgramacion> ofertados = new ArrayList<>();
        List<CursoProgramacion> nuevos = new ArrayList<>();
        for (CursoOfertado o : oferta.values()) {
            CohortePeriodo cohorte = o.cohorte();
            Asignatura asig = o.pensum().getAsignatura();
            String nombreSnapshot = o.pensum().getNameCourseSnapshot() != null
                    && !o.pensum().getNameCourseSnapshot().isBlank()
                            ? o.pensum().getNameCourseSnapshot()
                            : asig.getNameCourse();

            CursoProgramacion curso = existentesPorClave.get(o.clave());
            if (curso == null) {
                curso = CursoProgramacion.builder()
                        .periodo(periodo)
                        .programa(cohorte.getPrograma())
                        .asignatura(asig)
                        .jornada(cohorte.getJornada())
                        .estado(CursoProgramacion.ESTADO_PROGRAMADO)
                        .build();
                nuevos.add(curso);
            } else if (!curso.isExcluido()) {
                curso.setEstado(curso.getDocente() != null
                        ? CursoProgramacion.ESTADO_ASIGNADO
                        : CursoProgramacion.ESTADO_PROGRAMADO);
            }
            curso.setSemestre(cohorte.getSemestre());
            curso.setNumeroResolucion(cohorte.getNumeroResolucion());
            curso.setCredits(asig.getCredits() != null ? asig.getCredits() : 0);
            curso.setNombreAsignatura(nombreSnapshot);
            ofertados.add(curso);
        }
        // Los existentes que ya no están en la oferta no se borran: conservan su grupo
        // y quedan
        // con «Requiere revisión» para que el administrador los excluya con
        // justificación (decisión 2026-11-06).

        // 4. Grupos (HU-046) estables: conservan el orden de los que ya tenían grupo;
        // los nuevos van al final.
        List<CursoProgramacion> todos = new ArrayList<>(existentes);
        todos.addAll(nuevos);
        todos.stream()
                .collect(Collectors.groupingBy(c -> c.getAsignatura().getCodeCourse()))
                .values()
                .forEach(this::numerarGrupos);

        cursoRepository.saveAll(todos);

        // HU-049 criterio 4: lo que el docente dicta en el periodo pasa al histórico.
        docenteAsignaturaService.registrarDesdeCursos(ofertados);

        return construirResumen(periodoId, "Programación generada exitosamente con " + ofertados.size() + " cursos.");
    }

    @Transactional(readOnly = true)
    public List<ProgramacionDto.CursoResponse> obtenerProgramacion(Long periodoId, String codeProgram,
            String codeCourse, Jornada jornada) {
        periodoRepository.findById(periodoId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Período académico no encontrado con ID: " + periodoId));

        List<CursoProgramacion> lista = (codeProgram != null && !codeProgram.isBlank())
                ? cursoRepository.findByPeriodoIdAndCodeProgramWithDetails(periodoId, codeProgram)
                : cursoRepository.findByPeriodoIdWithDetails(periodoId);

        // HU-047 criterio 4: «Requiere revisión» se calcula comparando con la oferta
        // actual.
        List<CohortePeriodo> cohortes = cohorteRepository.findByPeriodoIdAndConfirmadaTrue(periodoId);
        if (codeProgram != null && !codeProgram.isBlank()) {
            cohortes = cohortes.stream()
                    .filter(c -> c.getPrograma() != null
                            && codeProgram.equalsIgnoreCase(c.getPrograma().getCodeProgram()))
                    .collect(Collectors.toList());
        }
        Map<String, CursoOfertado> oferta = calcularOferta(cohortes, false);
        Set<String> cohortesConfirmadas = cohortes.stream()
                .map(c -> c.getPrograma().getCodeProgram() + "#" + c.getSemestre() + "#" + c.getJornada())
                .collect(Collectors.toSet());

        return lista.stream()
                .filter(c -> codeCourse == null || codeCourse.isBlank()
                        || c.getAsignatura().getCodeCourse().equalsIgnoreCase(codeCourse))
                .filter(c -> jornada == null || c.getJornada() == jornada)
                .map(c -> toResponse(c, motivoRevision(c, oferta, cohortesConfirmadas)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProgramacionDto.ResumenResponse obtenerResumen(Long periodoId) {
        periodoRepository.findById(periodoId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Período académico no encontrado con ID: " + periodoId));
        return construirResumen(periodoId, "Resumen consolidado de la programación");
    }

    /**
     * Exporta la programación académica del período a un libro de Excel (.xlsx).
     *
     * @param periodoId   identificador del período académico
     * @param codeProgram código de programa opcional (para coordinadores se fuerza su programa propio)
     * @return arreglo de bytes del archivo Excel generado
     */
    @Transactional(readOnly = true)
    public byte[] exportarExcel(Long periodoId, String codeProgram) {
        PeriodoAcademico periodo = periodoRepository.findById(periodoId)
                .orElseThrow(() -> new ResourceNotFoundException("Período académico no encontrado con ID: " + periodoId));

        AccesoProgramaService.Alcance alcance = accesoProgramaService.alcanceActual();
        List<CursoProgramacion> cursos;
        if (alcance.admin()) {
            cursos = (codeProgram != null && !codeProgram.isBlank())
                    ? cursoRepository.findByPeriodoIdAndCodeProgramWithDetails(periodoId, codeProgram.trim())
                    : cursoRepository.findByPeriodoIdWithDetails(periodoId);
        } else if (codeProgram != null && !codeProgram.isBlank()
                && ((alcance.codeProgram() != null && alcance.codeProgram().equalsIgnoreCase(codeProgram.trim()))
                        || accesoProgramaService.tieneAccesoAPrograma(codeProgram.trim()))) {
            cursos = cursoRepository.findByPeriodoIdAndCodeProgramWithDetails(periodoId, codeProgram.trim());
        } else {
            Set<String> permitidos = accesoProgramaService.programasPermitidos();
            if (permitidos != null && permitidos.size() > 1) {
                cursos = cursoRepository.findByPeriodoIdWithDetails(periodoId).stream()
                        .filter(c -> c.getPrograma() != null
                                && c.getPrograma().getCodeProgram() != null
                                && permitidos.contains(c.getPrograma().getCodeProgram().trim().toUpperCase()))
                        .toList();
            } else {
                String programaFiltrar = (permitidos != null && !permitidos.isEmpty())
                        ? permitidos.iterator().next()
                        : alcance.codeProgram();
                cursos = (programaFiltrar != null && !programaFiltrar.isBlank())
                        ? cursoRepository.findByPeriodoIdAndCodeProgramWithDetails(periodoId, programaFiltrar.trim())
                        : List.of();
            }
        }

        List<CursoProgramacion> activos = new ArrayList<>();
        for (CursoProgramacion c : cursos) {
            if (!c.isExcluido()) {
                activos.add(c);
            }
        }
        activos.sort((c1, c2) -> {
            String p1 = c1.getPrograma() != null && c1.getPrograma().getCodeProgram() != null ? c1.getPrograma().getCodeProgram() : "";
            String p2 = c2.getPrograma() != null && c2.getPrograma().getCodeProgram() != null ? c2.getPrograma().getCodeProgram() : "";
            int cmpProg = p1.compareTo(p2);
            if (cmpProg != 0) return cmpProg;

            int s1 = c1.getSemestre() != null ? c1.getSemestre() : 0;
            int s2 = c2.getSemestre() != null ? c2.getSemestre() : 0;
            int cmpSem = Integer.compare(s1, s2);
            if (cmpSem != 0) return cmpSem;

            String n1 = c1.getNombreAsignatura() != null ? c1.getNombreAsignatura() : "";
            String n2 = c2.getNombreAsignatura() != null ? c2.getNombreAsignatura() : "";
            int cmpNom = n1.compareToIgnoreCase(n2);
            if (cmpNom != 0) return cmpNom;

            int g1 = c1.getGrupo() != null ? c1.getGrupo() : 0;
            int g2 = c2.getGrupo() != null ? c2.getGrupo() : 0;
            return Integer.compare(g1, g2);
        });

        return programacionExcelService.generar(periodo, activos);
    }

    // ------------------------------------------------------------------ HU-047

    /**
     * Criterio 2: Como quedarian los grupos si se excluye el curso, sin guardar nada.
     */
    @Transactional(readOnly = true)
    public ProgramacionDto.AjusteGruposResponse vistaPreviaExclusion(Long cursoId) {
        CursoProgramacion curso = buscarCurso(cursoId);
        if (curso.isExcluido()) {
            throw new ConflictException(MSG_YA_EXCLUIDO);
        }
        List<CursoProgramacion> cursos = cursoRepository.findByPeriodoAndAsignatura(
                curso.getPeriodo().getId(), curso.getAsignatura().getCodeCourse());
        Map<Long, Integer> antes = gruposActuales(cursos);
        Map<Long, Integer> despues = planExclusion(cursos, cursoId);
        Set<Long> excluidos = cursos.stream()
                .filter(c -> c.isExcluido() || c.getId().equals(cursoId))
                .map(CursoProgramacion::getId)
                .collect(Collectors.toSet());
        return construirAjuste(curso, cursos, antes, despues, excluidos);
    }

    /**
     * Criterio 2: excluye un curso con justificación y renumera los grupos activos
     * de la asignatura
     * desde 50 conservando su orden. Si tenía docente, se le quita (el frontend
     * avisa antes).
     * Criterio 5: cada exclusión y cada renumeración van a la bitácora en la misma
     * transacción.
     */
    @Transactional
    public ProgramacionDto.AjusteGruposResponse excluir(Long cursoId, ProgramacionDto.JustificacionRequest request) {
        String justificacion = validarJustificacion(request);
        CursoProgramacion curso = buscarCurso(cursoId);
        Long periodoId = curso.getPeriodo().getId();
        List<CursoProgramacion> cursos = cursoRepository.findByPeriodoAndAsignaturaForUpdate(
                periodoId, curso.getAsignatura().getCodeCourse());
        CursoProgramacion objetivo = cursos.stream()
                .filter(c -> c.getId().equals(cursoId))
                .findFirst()
                .orElse(curso);
        if (objetivo.isExcluido()) {
            throw new ConflictException(MSG_YA_EXCLUIDO);
        }

        Map<Long, Integer> antes = gruposActuales(cursos);
        Map<Long, Integer> plan = planExclusion(cursos, cursoId);

        Map<String, Object> anterior = new LinkedHashMap<>();
        anterior.put("codigoCurso", codigoCurso(objetivo));
        anterior.put("grupo", objetivo.getGrupo());
        anterior.put("estado", objetivo.getEstado());
        anterior.put("docenteId", objetivo.getDocente() != null ? objetivo.getDocente().getId() : null);
        anterior.put("codeProgram", objetivo.getPrograma().getCodeProgram());
        anterior.put("jornada", String.valueOf(objetivo.getJornada()));

        objetivo.setEstado(CursoProgramacion.ESTADO_EXCLUIDO);
        objetivo.setGrupo(null);
        objetivo.setDocente(null);
        objetivo.setDia(null);
        objetivo.setHoraInicio(null);
        objetivo.setHoraFin(null);
        objetivo.setDia2(null);
        objetivo.setHoraInicio2(null);
        objetivo.setHoraFin2(null);
        objetivo.setHorario(null);
        objetivo.setJustificacionExclusion(justificacion);
        objetivo.setExcluidoPor(correoSesion());
        objetivo.setFechaExclusion(Instant.now());

        Map<String, Object> nuevo = new LinkedHashMap<>();
        nuevo.put("estado", objetivo.getEstado());
        nuevo.put("justificacion", justificacion);
        bitacoraService.registrar(AccionBitacora.DESACTIVAR, ENTIDAD_CURSO, String.valueOf(cursoId), periodoId,
                anterior, nuevo);

        aplicarRenumeracion(cursos, antes, plan, periodoId);
        cursoRepository.saveAll(cursos);

        Set<Long> excluidos = cursos.stream().filter(CursoProgramacion::isExcluido)
                .map(CursoProgramacion::getId).collect(Collectors.toSet());
        return construirAjuste(objetivo, cursos, antes, plan, excluidos);
    }

    /**
     * Deshace una exclusión: el curso vuelve a PROGRAMADO, sin docente, con el
     * siguiente número de la serie.
     * Los demás cursos no cambian de grupo (decisión 2026-11-06).
     */
    @Transactional
    public ProgramacionDto.AjusteGruposResponse reactivar(Long cursoId, ProgramacionDto.JustificacionRequest request) {
        String justificacion = validarJustificacion(request);
        CursoProgramacion curso = buscarCurso(cursoId);
        Long periodoId = curso.getPeriodo().getId();
        List<CursoProgramacion> cursos = cursoRepository.findByPeriodoAndAsignaturaForUpdate(
                periodoId, curso.getAsignatura().getCodeCourse());
        CursoProgramacion objetivo = cursos.stream()
                .filter(c -> c.getId().equals(cursoId))
                .findFirst()
                .orElse(curso);
        if (!objetivo.isExcluido()) {
            throw new ConflictException(MSG_NO_EXCLUIDO);
        }

        Map<Long, Integer> antes = gruposActuales(cursos);
        int siguiente = cursos.stream()
                .filter(c -> !c.isExcluido() && c.getGrupo() != null)
                .mapToInt(CursoProgramacion::getGrupo)
                .max()
                .orElse(CursoProgramacion.GRUPO_INICIAL - 1) + 1;

        Map<String, Object> anterior = new LinkedHashMap<>();
        anterior.put("estado", objetivo.getEstado());
        anterior.put("justificacionExclusion", objetivo.getJustificacionExclusion());
        anterior.put("excluidoPor", objetivo.getExcluidoPor());
        anterior.put("fechaExclusion",
                objetivo.getFechaExclusion() != null ? objetivo.getFechaExclusion().toString() : null);

        objetivo.setEstado(CursoProgramacion.ESTADO_PROGRAMADO);
        objetivo.setGrupo(siguiente);
        objetivo.setJustificacionExclusion(null);
        objetivo.setExcluidoPor(null);
        objetivo.setFechaExclusion(null);

        Map<String, Object> nuevo = new LinkedHashMap<>();
        nuevo.put("estado", objetivo.getEstado());
        nuevo.put("grupo", siguiente);
        nuevo.put("codigoCurso", codigoCurso(objetivo));
        nuevo.put("justificacion", justificacion);
        bitacoraService.registrar(AccionBitacora.REACTIVAR, ENTIDAD_CURSO, String.valueOf(cursoId), periodoId,
                anterior, nuevo);

        cursoRepository.save(objetivo);

        Map<Long, Integer> despues = gruposActuales(cursos);
        Set<Long> excluidos = cursos.stream().filter(CursoProgramacion::isExcluido)
                .map(CursoProgramacion::getId).collect(Collectors.toSet());
        return construirAjuste(objetivo, cursos, antes, despues, excluidos);
    }

    /**
     * HU-059: parámetros de edición para el frontend. El administrador siempre
     * puede editar.
     */
    @Transactional(readOnly = true)
    public ProgramacionDto.CuposConfigResponse obtenerConfiguracionCupos(Long periodoId) {
        PeriodoAcademico periodo = periodoRepository.findById(periodoId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Período académico no encontrado con ID: " + periodoId));
        AccesoProgramaService.Alcance alcance = accesoProgramaService.alcanceActual();
        boolean plazoAbierto = plazoCoordinadorAbierto(periodo);
        return ProgramacionDto.CuposConfigResponse.builder()
                .maxPorGrupo(cuposProperties.getMaxPorGrupo())
                .plazoAbierto(plazoAbierto)
                .puedeEditar(alcance.admin() || plazoAbierto)
                .admin(alcance.admin())
                .build();
    }

    /**
     * HU-059: registra los cupos de los cursos activos. El coordinador solo edita
     * su programa y
     * dentro del plazo; el administrador siempre puede. Cada cambio queda en la
     * bitácora.
     */
    @Transactional
    public ProgramacionDto.ActualizarCuposResponse actualizarCupos(ProgramacionDto.ActualizarCuposRequest request) {
        if (request == null || request.periodoId() == null) {
            throw new BadRequestException(MSG_CUPOS_SIN_DATOS);
        }
        if (request.cursos() == null || request.cursos().isEmpty()) {
            throw new BadRequestException(MSG_CUPOS_SIN_DATOS);
        }

        Long periodoId = request.periodoId();
        PeriodoAcademico periodo = periodoRepository.findById(periodoId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Período académico no encontrado con ID: " + periodoId));
        AccesoProgramaService.Alcance alcance = accesoProgramaService.alcanceActual();

        if (!alcance.admin() && !plazoCoordinadorAbierto(periodo)) {
            throw new ConflictException(MSG_PLAZO_CERRADO);
        }

        Map<Long, Integer> cuposSolicitados = new LinkedHashMap<>();
        for (ProgramacionDto.CupoItemRequest item : request.cursos()) {
            if (item == null || item.cursoId() == null) {
                throw new BadRequestException(MSG_CUPO_INVALIDO);
            }
            validarCupo(item.cupos());
            cuposSolicitados.put(item.cursoId(), item.cupos());
        }

        // Una sola consulta para todos los cursos enviados; evita el N+1 al guardar
        // fila por fila.
        List<CursoProgramacion> encontrados = cursoRepository
                .findAllByIdWithDetails(new ArrayList<>(cuposSolicitados.keySet()));
        Map<Long, CursoProgramacion> cursos = new LinkedHashMap<>();
        for (CursoProgramacion curso : encontrados) {
            cursos.put(curso.getId(), curso);
        }

        for (Long cursoId : cuposSolicitados.keySet()) {
            CursoProgramacion curso = cursos.get(cursoId);
            if (curso == null) {
                throw new ResourceNotFoundException(MSG_CURSO_NO_ENCONTRADO);
            }
            if (!curso.getPeriodo().getId().equals(periodoId)) {
                throw new BadRequestException(MSG_CURSO_OTRO_PERIODO);
            }
            if (!alcance.admin()) {
                accesoProgramaService.exigirProgramaPropio(curso.getPrograma().getCodeProgram());
            }
            if (curso.isExcluido()) {
                throw new BadRequestException(MSG_CUPO_EXCLUIDO);
            }
        }

        for (Map.Entry<Long, Integer> entrada : cuposSolicitados.entrySet()) {
            CursoProgramacion curso = cursos.get(entrada.getKey());
            Integer anteriorCupos = curso.getCupos();
            Integer nuevoCupos = entrada.getValue();
            if (Objects.equals(anteriorCupos, nuevoCupos)) {
                continue;
            }
            Map<String, Object> anterior = new LinkedHashMap<>();
            anterior.put("codigoCurso", codigoCurso(curso));
            anterior.put("cupos", anteriorCupos);
            Map<String, Object> nuevo = new LinkedHashMap<>();
            nuevo.put("codigoCurso", codigoCurso(curso));
            nuevo.put("cupos", nuevoCupos);
            curso.setCupos(nuevoCupos);
            bitacoraService.registrar(AccionBitacora.EDITAR, ENTIDAD_CURSO, String.valueOf(curso.getId()),
                    periodoId, anterior, nuevo);
        }

        cursoRepository.saveAll(cursos.values());

        List<ProgramacionDto.CursoResponse> respuesta = cursos.values().stream()
                .map(c -> toResponse(c, null))
                .toList();
        return new ProgramacionDto.ActualizarCuposResponse(respuesta, MSG_CUPOS_GUARDADOS);
    }

    private void validarCupo(Integer cupos) {
        if (cupos == null || cupos <= 0) {
            throw new BadRequestException(MSG_CUPO_INVALIDO);
        }
        if (cupos > cuposProperties.getMaxPorGrupo()) {
            throw new BadRequestException(String.format(MSG_CUPO_EXCEDE_MAX, cuposProperties.getMaxPorGrupo()));
        }
    }

    static boolean plazoCoordinadorAbierto(PeriodoAcademico periodo) {
        if (periodo == null || periodo.getPlazoCoordinadorInicio() == null
                || periodo.getPlazoCoordinadorFin() == null) {
            return false;
        }
        Instant ahora = Instant.now();
        return !ahora.isBefore(periodo.getPlazoCoordinadorInicio())
                && !ahora.isAfter(periodo.getPlazoCoordinadorFin());
    }

    /**
     * El administrador siempre puede; el coordinador respeta el plazo del período.
     */
    private void exigirPlazoCoordinadorAbierto(PeriodoAcademico periodo) {
        AccesoProgramaService.Alcance alcance = accesoProgramaService.alcanceActual();
        if (!alcance.admin() && !plazoCoordinadorAbierto(periodo)) {
            throw new ConflictException(MSG_PLAZO_CERRADO);
        }
    }

    /**
     * Electivas programadas a mano: vuelve a numerar desde 50 los grupos activos de
     * la asignatura
     * después de quitar un curso o cambiar su asignatura. Cada cambio va a la
     * bitácora.
     */
    @Transactional
    public void renumerarGrupos(Long periodoId, String codeCourse) {
        List<CursoProgramacion> cursos = cursoRepository.findByPeriodoAndAsignaturaForUpdate(periodoId, codeCourse);
        Map<Long, Integer> antes = gruposActuales(cursos);
        Map<Long, Integer> plan = planExclusion(cursos, null);
        aplicarRenumeracion(cursos, antes, plan, periodoId);
        cursoRepository.saveAll(cursos);
    }

    // ------------------------------------------------------------------ apoyo

    private static String clave(String codeProgram, String codeCourse, Jornada jornada) {
        return codeProgram + "#" + codeCourse + "#" + jornada;
    }

    private static String clave(CursoProgramacion c) {
        return clave(c.getPrograma().getCodeProgram(), c.getAsignatura().getCodeCourse(), c.getJornada());
    }

    /**
     * Cursos que corresponden según las cohortes y sus mallas (sin electivas
     * profesionales, HU-048).
     * Con {@code estricta} se exige que cada cohorte tenga asignaturas obligatorias
     * (HU-045 criterio 4).
     */
    private Map<String, CursoOfertado> calcularOferta(List<CohortePeriodo> cohortes, boolean estricta) {
        Map<String, CursoOfertado> oferta = new LinkedHashMap<>();
        for (CohortePeriodo cohorte : cohortes) {
            Programa prog = cohorte.getPrograma();
            List<PensumAcademico> materiasMalla = pensumRepository.findActiveByCodeProgramAndResolucionAndSemestre(
                    prog.getCodeProgram(), cohorte.getNumeroResolucion(), cohorte.getSemestre());

            // Las mallas creadas antes de V20 no tenían número de resolución.
            // En ese caso, la cohorte conserva la resolución seleccionada, pero
            // las asignaturas deben localizarse por programa y semestre.
            if (materiasMalla.isEmpty()) {
                materiasMalla = pensumRepository.findActiveByCodeProgramAndSemestre(
                        prog.getCodeProgram(), cohorte.getSemestre());
            }

            // Excluir electivas profesionales
            List<PensumAcademico> obligatorias = materiasMalla.stream()
                    .filter(p -> p.getIsElective() == null || !p.getIsElective())
                    .collect(Collectors.toList());

            if (obligatorias.isEmpty() && estricta) {
                throw new BadRequestException("La cohorte de semestre " + cohorte.getSemestre() + " del programa " +
                        prog.getNameProgram() + " con resolución '" + cohorte.getNumeroResolucion()
                        + "' no tiene asignaturas obligatorias en la malla.");
            }

            for (PensumAcademico pensum : obligatorias) {
                // Los espacios «Electiva Profesional N» (y la complementaria) son el esqueleto
                // de la malla, no
                // cursos: la electiva real la programa el coordinador
                // (ElectivaProgramadaService).
                if (pensum.getId() != null && pensum.getId().getCodeCourse() != null
                        && pensum.getId().getCodeCourse().toUpperCase()
                                .startsWith(ElectivaProgramadaService.PREFIJO_ESPACIO)) {
                    continue;
                }
                CursoOfertado ofertado = new CursoOfertado(cohorte, pensum);
                // Evitar duplicar materia en la misma jornada para el mismo programa
                oferta.putIfAbsent(ofertado.clave(), ofertado);
            }
        }
        return oferta;
    }

    /**
     * HU-047 criterio 4: motivo por el que un curso activo requiere revisión, o
     * null.
     */
    private String motivoRevision(CursoProgramacion c, Map<String, CursoOfertado> oferta,
            Set<String> cohortesConfirmadas) {
        if (c.isExcluido()) {
            return null;
        }
        if (Boolean.FALSE.equals(c.getAsignatura().getActivo())) {
            return REVISION_ASIGNATURA_INACTIVA;
        }
        if (ElectivaProgramadaService.esPropuesta(c.getCodeCourseEspacio())) {
            // Complementaria propuesta por el coordinador: no pertenece a una cohorte ni a la malla.
            return null;
        }
        if (!cohortesConfirmadas.contains(c.getPrograma().getCodeProgram() + "#" + c.getSemestre() + "#" + c.getJornada())) {
            return REVISION_SIN_COHORTE;
        }
        if (c.getCodeCourseEspacio() != null) {
            // Electiva programada a mano: no sale de la malla obligatoria, no es «fuera de
            // malla».
            return null;
        }
        if (!oferta.containsKey(clave(c))) {
            return REVISION_FUERA_DE_MALLA;
        }
        return null;
    }

    /**
     * Orden de la serie: primero por grupo actual; los que no tienen grupo, por
     * programa y jornada.
     */
    private static final Comparator<CursoProgramacion> ORDEN_SERIE = Comparator
            .comparing(CursoProgramacion::getGrupo, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(c -> c.getPrograma().getCodeProgram())
            .thenComparing(CursoProgramacion::getJornada);

    /**
     * Numera consecutivo desde 50 los cursos activos de una asignatura; los
     * excluidos quedan sin grupo.
     */
    private void numerarGrupos(List<CursoProgramacion> cursosDeMateria) {
        int grupo = CursoProgramacion.GRUPO_INICIAL;
        for (CursoProgramacion curso : cursosDeMateria.stream().sorted(ORDEN_SERIE).toList()) {
            if (curso.isExcluido()) {
                curso.setGrupo(null);
            } else {
                curso.setGrupo(grupo++);
            }
        }
    }

    /**
     * Grupo que tendría cada curso activo si se excluye {@code cursoExcluir}; los
     * excluidos quedan en null.
     */
    static Map<Long, Integer> planExclusion(List<CursoProgramacion> cursos, Long cursoExcluir) {
        Map<Long, Integer> plan = new HashMap<>();
        int grupo = CursoProgramacion.GRUPO_INICIAL;
        for (CursoProgramacion c : cursos.stream().sorted(ORDEN_SERIE).toList()) {
            if (c.isExcluido() || c.getId().equals(cursoExcluir)) {
                plan.put(c.getId(), null);
            } else {
                plan.put(c.getId(), grupo++);
            }
        }
        return plan;
    }

    private static Map<Long, Integer> gruposActuales(List<CursoProgramacion> cursos) {
        Map<Long, Integer> grupos = new HashMap<>();
        for (CursoProgramacion c : cursos) {
            grupos.put(c.getId(), c.getGrupo());
        }
        return grupos;
    }

    private void aplicarRenumeracion(List<CursoProgramacion> cursos, Map<Long, Integer> antes,
            Map<Long, Integer> plan, Long periodoId) {
        for (CursoProgramacion c : cursos) {
            if (c.isExcluido()) {
                continue;
            }
            Integer anteriorGrupo = antes.get(c.getId());
            Integer nuevoGrupo = plan.get(c.getId());
            if (Objects.equals(anteriorGrupo, nuevoGrupo)) {
                continue;
            }
            Map<String, Object> anterior = new LinkedHashMap<>();
            anterior.put("codigoCurso", codigoCurso(c.getAsignatura().getCodeCourse(), anteriorGrupo));
            anterior.put("grupo", anteriorGrupo);
            c.setGrupo(nuevoGrupo);
            Map<String, Object> nuevo = new LinkedHashMap<>();
            nuevo.put("codigoCurso", codigoCurso(c.getAsignatura().getCodeCourse(), nuevoGrupo));
            nuevo.put("grupo", nuevoGrupo);
            bitacoraService.registrar(AccionBitacora.EDITAR, ENTIDAD_CURSO, String.valueOf(c.getId()), periodoId,
                    anterior, nuevo);
        }
    }

    private ProgramacionDto.AjusteGruposResponse construirAjuste(CursoProgramacion curso,
            List<CursoProgramacion> cursos,
            Map<Long, Integer> antes, Map<Long, Integer> despues,
            Set<Long> excluidos) {
        List<ProgramacionDto.GrupoCambio> cambios = cursos.stream()
                .map(c -> ProgramacionDto.GrupoCambio.builder()
                        .id(c.getId())
                        .codeProgram(c.getPrograma().getCodeProgram())
                        .nombrePrograma(c.getPrograma().getNameProgram())
                        .jornada(c.getJornada())
                        .grupoAntes(antes.get(c.getId()))
                        .grupoDespues(excluidos.contains(c.getId()) ? null : despues.get(c.getId()))
                        .excluido(excluidos.contains(c.getId()))
                        .build())
                .sorted(Comparator.comparing(ProgramacionDto.GrupoCambio::getGrupoDespues,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProgramacionDto.GrupoCambio::getGrupoAntes,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProgramacionDto.GrupoCambio::getCodeProgram))
                .toList();

        return ProgramacionDto.AjusteGruposResponse.builder()
                .cursoId(curso.getId())
                .periodoId(curso.getPeriodo().getId())
                .codeCourse(curso.getAsignatura().getCodeCourse())
                .nombreAsignatura(curso.getNombreAsignatura())
                .tieneDocente(curso.getDocente() != null)
                .cursos(cambios)
                .build();
    }

    private CursoProgramacion buscarCurso(Long cursoId) {
        return cursoRepository.findByIdWithDetails(cursoId)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_CURSO_NO_ENCONTRADO));
    }

    private static String validarJustificacion(ProgramacionDto.JustificacionRequest request) {
        String justificacion = request != null && request.justificacion() != null ? request.justificacion().trim() : "";
        if (justificacion.isEmpty()) {
            throw new BadRequestException(MSG_JUSTIFICACION_OBLIGATORIA);
        }
        if (justificacion.length() > MAX_JUSTIFICACION) {
            throw new BadRequestException(MSG_JUSTIFICACION_LARGA);
        }
        return justificacion;
    }

    /** Correo del administrador de la sesión, para excluido_por. */
    private static String correoSesion() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt && jwt.getClaimAsString("email") != null) {
            return jwt.getClaimAsString("email");
        }
        throw new IllegalStateException("No hay un usuario autenticado para registrar la exclusión");
    }

    private static String codigoCurso(CursoProgramacion c) {
        return codigoCurso(c.getAsignatura().getCodeCourse(), c.getGrupo());
    }

    private static String codigoCurso(String codeCourse, Integer grupo) {
        return grupo == null ? null : codeCourse + "-" + grupo;
    }

    private ProgramacionDto.ResumenResponse construirResumen(Long periodoId, String mensaje) {
        List<Object[]> conteos = cursoRepository.contarCursosPorPrograma(periodoId);
        List<ProgramacionDto.ResumenProgramaItem> items = new ArrayList<>();
        long total = 0;

        for (Object[] row : conteos) {
            String code = (String) row[0];
            String nombre = (String) row[1];
            long cantidad = ((Number) row[2]).longValue();
            total += cantidad;

            items.add(ProgramacionDto.ResumenProgramaItem.builder()
                    .codeProgram(code)
                    .nombrePrograma(nombre)
                    .totalCursos(cantidad)
                    .build());
        }

        return ProgramacionDto.ResumenResponse.builder()
                .totalCursos(total)
                .programas(items)
                .mensaje(mensaje)
                .build();
    }

    @Transactional
    public ProgramacionDto.CursoResponse asignarDocente(Long cursoId, Long idDocente,
            boolean reemplazarDocente) {
        return asignarDocente(cursoId, idDocente, reemplazarDocente, null, null, null, null);
    }

    @Transactional
    public ProgramacionDto.CursoResponse asignarDocente(Long cursoId, Long idDocente,
            boolean reemplazarDocente, Integer dia, java.time.LocalTime horaInicio,
            java.time.LocalTime horaFin, String horario) {
        return asignarDocente(cursoId, idDocente, reemplazarDocente, dia, horaInicio, horaFin, null, null, null,
                horario);
    }

    @Transactional
    public ProgramacionDto.CursoResponse asignarDocente(Long cursoId, Long idDocente,
            boolean reemplazarDocente, Integer dia, java.time.LocalTime horaInicio,
            java.time.LocalTime horaFin, Integer dia2, java.time.LocalTime horaInicio2,
            java.time.LocalTime horaFin2, String horario) {
        CursoProgramacion curso = cursoRepository.findByIdWithDetails(cursoId)
                .orElseThrow(() -> new ResourceNotFoundException("Curso no encontrado con id " + cursoId));

        // El coordinador solo asigna en su programa y dentro del plazo configurado.
        accesoProgramaService.exigirProgramaPropio(curso.getPrograma().getCodeProgram());
        exigirPlazoCoordinadorAbierto(curso.getPeriodo());

        if (curso.isExcluido()) {
            throw new BadRequestException("No se puede asignar un docente a un curso excluido");
        }

        Docente docenteAnterior = curso.getDocente();
        if (idDocente != null) {
            if (docenteAnterior != null && !docenteAnterior.getId().equals(idDocente)
                    && !reemplazarDocente) {
                throw new ConflictException(
                        "El curso ya tiene un docente asignado. Confirme si desea reemplazarlo.");
            }
            // Bloquea la fila del docente: dos asignaciones simultáneas no pueden pasar de
            // 20 h
            Docente docente = docenteRepository.findByIdForUpdate(idDocente)
                    .orElseThrow(() -> new ResourceNotFoundException("Docente no encontrado con id " + idDocente));
            if (Boolean.FALSE.equals(docente.getEstado())) {
                throw new BadRequestException("El docente seleccionado está inactivo");
            }
            Integer horas = curso.getAsignatura().getHorasSemanales();
            if (horas == null) {
                throw new BadRequestException(MSG_ASIGNATURA_SIN_HORAS);
            }

            List<DisponibilidadDocente> franjas = disponibilidadDocenteRepository
                    .findAllByDocenteIdAndPeriodoIdOrderByDiaAscHoraInicioAsc(
                            idDocente, curso.getPeriodo().getId());
            if (franjas.isEmpty()) {
                throw new BadRequestException(
                        "El docente seleccionado no tiene disponibilidad horaria registrada para este período");
            }

            boolean esHorarioDividido = dia != null && horaInicio != null && horaFin != null
                    && dia2 != null && horaInicio2 != null && horaFin2 != null;
            boolean esHorarioUnico = dia != null && horaInicio != null && horaFin != null
                    && !esHorarioDividido;

            if (esHorarioDividido) {
                if (dia.equals(dia2)) {
                    throw new BadRequestException("Los dos días de clase deben ser diferentes");
                }
                if (!horaFin.isAfter(horaInicio) || !horaFin2.isAfter(horaInicio2)) {
                    throw new BadRequestException(
                            "La hora de fin debe ser posterior a la hora de inicio en ambas sesiones");
                }
                long duracionMin1 = java.time.Duration.between(horaInicio, horaFin).toMinutes();
                long duracionMin2 = java.time.Duration.between(horaInicio2, horaFin2).toMinutes();
                long duracionTotalMin = duracionMin1 + duracionMin2;

                if (duracionTotalMin < horas * 60L) {
                    throw new BadRequestException(String.format(
                            "Las sesiones seleccionadas deben cubrir las %d horas presenciales del curso (tienen %.1f h)",
                            horas, duracionTotalMin / 60.0));
                }

                boolean dentroDeDisponibilidad1 = franjas.stream().anyMatch(f -> f.getDia().equals(dia)
                        && !f.getHoraInicio().isAfter(horaInicio)
                        && !f.getHoraFin().isBefore(horaFin));
                if (!dentroDeDisponibilidad1) {
                    throw new BadRequestException(
                            "El horario de la sesión 1 no se encuentra dentro de la disponibilidad horaria del docente");
                }

                boolean dentroDeDisponibilidad2 = franjas.stream().anyMatch(f -> f.getDia().equals(dia2)
                        && !f.getHoraInicio().isAfter(horaInicio2)
                        && !f.getHoraFin().isBefore(horaFin2));
                if (!dentroDeDisponibilidad2) {
                    throw new BadRequestException(
                            "El horario de la sesión 2 no se encuentra dentro de la disponibilidad horaria del docente");
                }

                // Validar que el docente no tenga otro curso activo en el mismo período y
                // horario en sesión 1 o sesión 2
                List<CursoProgramacion> cursosDocente = cursoRepository
                        .findByPeriodoIdAndDocenteId(curso.getPeriodo().getId(), idDocente).stream()
                        .filter(c -> !c.getId().equals(curso.getId()) && !c.isExcluido())
                        .toList();

                for (CursoProgramacion otro : cursosDocente) {
                    validarCruceHorario(dia, horaInicio, horaFin, otro, "sesión 1");
                    validarCruceHorario(dia2, horaInicio2, horaFin2, otro, "sesión 2");
                }
            } else if (esHorarioUnico) {
                if (!horaFin.isAfter(horaInicio)) {
                    throw new BadRequestException("La hora de fin debe ser posterior a la hora de inicio");
                }
                long duracionMin = java.time.Duration.between(horaInicio, horaFin).toMinutes();
                if (duracionMin < horas * 60L) {
                    throw new BadRequestException(String.format(
                            "La franja horaria seleccionada debe cubrir al menos las %d horas presenciales del curso (tiene %.1f h)",
                            horas, duracionMin / 60.0));
                }
                boolean dentroDeDisponibilidad = franjas.stream().anyMatch(f -> f.getDia().equals(dia)
                        && !f.getHoraInicio().isAfter(horaInicio)
                        && !f.getHoraFin().isBefore(horaFin));
                if (!dentroDeDisponibilidad) {
                    throw new BadRequestException(
                            "El horario seleccionado no se encuentra dentro de la disponibilidad horaria del docente");
                }
                // Validar que el docente no tenga otro curso activo en el mismo período y
                // horario
                List<CursoProgramacion> cursosDocente = cursoRepository
                        .findByPeriodoIdAndDocenteId(curso.getPeriodo().getId(), idDocente).stream()
                        .filter(c -> !c.getId().equals(curso.getId()) && !c.isExcluido())
                        .toList();
                for (CursoProgramacion otro : cursosDocente) {
                    validarCruceHorario(dia, horaInicio, horaFin, otro, "el horario seleccionado");
                }
            }

            long minutosDisponibles = 0L;
            for (DisponibilidadDocente f : franjas) {
                if (f.getHoraInicio() != null && f.getHoraFin() != null) {
                    long min = java.time.Duration.between(f.getHoraInicio(), f.getHoraFin()).toMinutes();
                    if (min > 0) {
                        minutosDisponibles += min;
                    }
                }
            }
            long horasYaAsignadas = cursoRepository.sumarHorasExcluyendo(
                    idDocente, curso.getPeriodo().getId(), curso.getId());
            long minutosRestantes = minutosDisponibles - horasYaAsignadas * 60L;
            if (minutosRestantes < horas * 60L) {
                throw new ConflictException(String.format(
                        "El docente tiene %.2f h disponibles, %d h ya asignadas y necesita %d h para esta materia",
                        minutosDisponibles / 60.0, horasYaAsignadas, horas));
            }

            // La carga se valida para cualquier fila de docentes.id, sin importar el rol en
            // usuarios.
            cargaDocenteService.validar(idDocente, curso.getPeriodo().getId(), curso.getId(), horas);

            curso.setDocente(docente);
            curso.setDia(dia);
            curso.setHoraInicio(horaInicio);
            curso.setHoraFin(horaFin);
            curso.setDia2(dia2);
            curso.setHoraInicio2(horaInicio2);
            curso.setHoraFin2(horaFin2);
            curso.setHorario(horario);
            curso.setEstado(CursoProgramacion.ESTADO_ASIGNADO);

            docenteAsignaturaService.registrarDesdeCursos(List.of(curso));
            if (docenteAnterior != null && !docenteAnterior.getId().equals(idDocente)
                    && cursoRepository.countActivosByPeriodoAsignaturaDocenteExcludingCurso(
                            curso.getPeriodo().getId(), curso.getAsignatura().getCodeCourse(),
                            docenteAnterior.getId(), curso.getId()) == 0) {
                docenteAsignaturaService.desactivarAsociacion(docenteAnterior, curso);
            }
        } else {
            curso.setDocente(null);
            curso.setDia(null);
            curso.setHoraInicio(null);
            curso.setHoraFin(null);
            curso.setDia2(null);
            curso.setHoraInicio2(null);
            curso.setHoraFin2(null);
            curso.setHorario(null);
            curso.setEstado(CursoProgramacion.ESTADO_PROGRAMADO);
        }

        CursoProgramacion guardado = cursoRepository.save(curso);
        return toResponse(guardado, null);
    }

    @Transactional
    public ProgramacionDto.CursoResponse asignarDocentePorMateria(
            Long periodoId, String codeProgram, String codeCourse, Integer semestre, Long idDocente,
            boolean reemplazarDocente) {
        return asignarDocentePorMateria(periodoId, codeProgram, codeCourse, semestre, idDocente, reemplazarDocente,
                null, null, null, null);
    }

    @Transactional
    public ProgramacionDto.CursoResponse asignarDocentePorMateria(
            Long periodoId, String codeProgram, String codeCourse, Integer semestre, Long idDocente,
            boolean reemplazarDocente, Integer dia, java.time.LocalTime horaInicio,
            java.time.LocalTime horaFin, String horario) {
        return asignarDocentePorMateria(periodoId, codeProgram, codeCourse, semestre, idDocente, reemplazarDocente,
                dia, horaInicio, horaFin, null, null, null, horario);
    }

    @Transactional
    public ProgramacionDto.CursoResponse asignarDocentePorMateria(
            Long periodoId, String codeProgram, String codeCourse, Integer semestre, Long idDocente,
            boolean reemplazarDocente, Integer dia, java.time.LocalTime horaInicio,
            java.time.LocalTime horaFin, Integer dia2, java.time.LocalTime horaInicio2,
            java.time.LocalTime horaFin2, String horario) {
        // El coordinador solo asigna en su programa (antes de tocar nada)
        accesoProgramaService.exigirProgramaPropio(codeProgram);

        if (!periodoRepository.existsById(periodoId)) {
            throw new ResourceNotFoundException("Período no encontrado");
        }
        if (!programaRepository.existsById(codeProgram)) {
            throw new ResourceNotFoundException("Programa no encontrado con código " + codeProgram);
        }
        if (!asignaturaRepository.existsById(codeCourse)) {
            throw new ResourceNotFoundException("Asignatura no encontrada con código " + codeCourse);
        }

        // Solo se asigna docente a cursos ya generados desde la malla; este método no
        // crea cursos.
        // (semestre se conserva en la firma por compatibilidad con el endpoint.)
        CursoProgramacion curso = cursoRepository.findByPeriodoAndAsignatura(periodoId, codeCourse).stream()
                .filter(c -> c.getPrograma().getCodeProgram().equalsIgnoreCase(codeProgram) && !c.isExcluido())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(MSG_CURSO_NO_PROGRAMADO));

        return asignarDocente(curso.getId(), idDocente, reemplazarDocente,
                dia, horaInicio, horaFin, dia2, horaInicio2, horaFin2, horario);
    }

    private void validarCruceHorario(Integer diaClase, java.time.LocalTime iniClase,
            java.time.LocalTime finClase, CursoProgramacion otro, String sesionDesc) {
        if (diaClase == null || iniClase == null || finClase == null || otro == null) {
            return;
        }
        if (otro.getDia() != null && otro.getDia().equals(diaClase)
                && otro.getHoraInicio() != null && otro.getHoraFin() != null) {
            if (iniClase.isBefore(otro.getHoraFin()) && finClase.isAfter(otro.getHoraInicio())) {
                throw new ConflictException("El docente ya tiene asignado otro curso en " + sesionDesc + " ("
                        + otro.getNombreAsignatura() + ": " + otro.getHorario() + ")");
            }
        }
        if (otro.getDia2() != null && otro.getDia2().equals(diaClase)
                && otro.getHoraInicio2() != null && otro.getHoraFin2() != null) {
            if (iniClase.isBefore(otro.getHoraFin2()) && finClase.isAfter(otro.getHoraInicio2())) {
                throw new ConflictException("El docente ya tiene asignado otro curso en " + sesionDesc + " ("
                        + otro.getNombreAsignatura() + ": " + otro.getHorario() + ")");
            }
        }
    }

    /**
     * ELE-PROF-I → «Electiva Profesional I»; null si el curso no ocupa un espacio
     * de electiva.
     */
    private static String nombreEspacio(String codigoEspacio) {
        if (ElectivaProgramadaService.esPropuesta(codigoEspacio)) {
            return "Electiva complementaria";
        }
        if (codigoEspacio == null
                || !codigoEspacio.toUpperCase().startsWith(ElectivaProgramadaService.PREFIJO_ESPACIO)) {
            return null;
        }
        if (ElectivaProgramadaService.esEspacioComplementaria(codigoEspacio)) {
            return "Electiva Complementaria";
        }
        return "Electiva Profesional " + codigoEspacio.substring(ElectivaProgramadaService.PREFIJO_ESPACIO.length());
    }

    private ProgramacionDto.CursoResponse toResponse(CursoProgramacion c, String motivoRevision) {
        String nombreDocente = null;
        Long idDocente = null;
        if (c.getDocente() != null) {
            idDocente = c.getDocente().getId();
            nombreDocente = c.getDocente().getNombres() + " " + c.getDocente().getApellidos();
        }

        return ProgramacionDto.CursoResponse.builder()
                .id(c.getId())
                .periodoId(c.getPeriodo().getId())
                .codeProgram(c.getPrograma().getCodeProgram())
                .nombrePrograma(c.getPrograma().getNameProgram())
                .codeCourse(c.getAsignatura().getCodeCourse())
                .nombreAsignatura(c.getNombreAsignatura())
                .credits(c.getCredits())
                .horasSemanales(c.getAsignatura().getHorasSemanales())
                .semestre(c.getSemestre())
                .jornada(c.getJornada())
                .numeroResolucion(c.getNumeroResolucion())
                .grupo(c.getGrupo())
                .cupos(c.getCupos())
                .codigoEspacio(c.getCodeCourseEspacio())
                .nombreEspacio(nombreEspacio(c.getCodeCourseEspacio()))
                .idDocente(idDocente)
                .nombreDocente(nombreDocente)
                .dia(c.getDia())
                .horaInicio(c.getHoraInicio())
                .horaFin(c.getHoraFin())
                .dia2(c.getDia2())
                .horaInicio2(c.getHoraInicio2())
                .horaFin2(c.getHoraFin2())
                .horario(c.getHorario())
                .estado(c.getEstado())
                .codigoCurso(codigoCurso(c))
                .justificacionExclusion(c.getJustificacionExclusion())
                .excluidoPor(c.getExcluidoPor())
                .fechaExclusion(c.getFechaExclusion())
                .requiereRevision(motivoRevision != null)
                .motivoRevision(motivoRevision)
                .build();
    }
}
