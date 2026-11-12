package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.ProgramacionDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.*;
import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgramacionService {

    // HU-047: mensajes (la HU no los define; ver docs/contratos/HU-047.md)
    public static final String MSG_JUSTIFICACION_OBLIGATORIA = "La justificación es obligatoria";
    public static final String MSG_JUSTIFICACION_LARGA = "La justificación no puede superar 500 caracteres";
    public static final String MSG_CURSO_NO_ENCONTRADO = "Curso no encontrado";
    public static final String MSG_YA_EXCLUIDO = "El curso ya está excluido";
    public static final String MSG_NO_EXCLUIDO = "El curso no está excluido";

    // HU-047 criterio 4: motivos de «Requiere revisión»
    public static final String REVISION_FUERA_DE_MALLA = "La asignatura ya no está en la malla del programa";
    public static final String REVISION_ASIGNATURA_INACTIVA = "La asignatura está inactiva en el catálogo";
    public static final String REVISION_SIN_COHORTE = "La cohorte ya no está confirmada en el periodo";

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
    private final AsignaturaRepository asignaturaRepository;

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
                .orElseThrow(() -> new ResourceNotFoundException("Período académico no encontrado con ID: " + periodoId));

        long conteoExistente = cursoRepository.countByPeriodoId(periodoId);
        if (conteoExistente > 0 && !sobrescribir) {
            throw new ConflictException("Ya existe una programación generada para este período con " + conteoExistente + " cursos. Requiere confirmación para regenerar.");
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
            throw new BadRequestException("No se puede generar la programación: faltan cohortes confirmadas para los programas: " +
                    String.join(", ", programasFaltantes));
        }

        // 2. Cursos que corresponden según cohortes y mallas (valida que cada cohorte tenga asignaturas)
        Map<String, CursoOfertado> oferta = calcularOferta(cohortesConfirmadas, true);

        // 3. HU-047: al regenerar no se borra nada (RN-13). Se concilia por programa#asignatura#jornada:
        // los cursos que siguen conservan id, docente, estado y exclusión; los nuevos se agregan.
        List<CursoProgramacion> existentes = conteoExistente > 0
                ? new ArrayList<>(cursoRepository.findByPeriodoIdWithDetails(periodoId))
                : new ArrayList<>();
        Map<String, CursoProgramacion> existentesPorClave = new HashMap<>();
        for (CursoProgramacion c : existentes) {
            existentesPorClave.putIfAbsent(clave(c), c);
        }

        List<CursoProgramacion> ofertados = new ArrayList<>();
        List<CursoProgramacion> nuevos = new ArrayList<>();
        for (CursoOfertado o : oferta.values()) {
            CohortePeriodo cohorte = o.cohorte();
            Asignatura asig = o.pensum().getAsignatura();
            String nombreSnapshot = o.pensum().getNameCourseSnapshot() != null && !o.pensum().getNameCourseSnapshot().isBlank()
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
        // Los existentes que ya no están en la oferta no se borran: conservan su grupo y quedan
        // con «Requiere revisión» para que el administrador los excluya con justificación (decisión 2026-11-06).

        // 4. Grupos (HU-046) estables: conservan el orden de los que ya tenían grupo; los nuevos van al final.
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
    public List<ProgramacionDto.CursoResponse> obtenerProgramacion(Long periodoId, String codeProgram, String codeCourse, Jornada jornada) {
        periodoRepository.findById(periodoId)
                .orElseThrow(() -> new ResourceNotFoundException("Período académico no encontrado con ID: " + periodoId));

        List<CursoProgramacion> lista = (codeProgram != null && !codeProgram.isBlank())
                ? cursoRepository.findByPeriodoIdAndCodeProgramWithDetails(periodoId, codeProgram)
                : cursoRepository.findByPeriodoIdWithDetails(periodoId);

        // HU-047 criterio 4: «Requiere revisión» se calcula comparando con la oferta actual.
        List<CohortePeriodo> cohortes = cohorteRepository.findByPeriodoIdAndConfirmadaTrue(periodoId);
        Map<String, CursoOfertado> oferta = calcularOferta(cohortes, false);
        Set<String> cohortesConfirmadas = cohortes.stream()
                .map(c -> c.getPrograma().getCodeProgram() + "#" + c.getSemestre() + "#" + c.getJornada())
                .collect(Collectors.toSet());

        return lista.stream()
                .filter(c -> codeCourse == null || codeCourse.isBlank() || c.getAsignatura().getCodeCourse().equalsIgnoreCase(codeCourse))
                .filter(c -> jornada == null || c.getJornada() == jornada)
                .map(c -> toResponse(c, motivoRevision(c, oferta, cohortesConfirmadas)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProgramacionDto.ResumenResponse obtenerResumen(Long periodoId) {
        periodoRepository.findById(periodoId)
                .orElseThrow(() -> new ResourceNotFoundException("Período académico no encontrado con ID: " + periodoId));
        return construirResumen(periodoId, "Resumen consolidado de la programación");
    }

    // ------------------------------------------------------------------ HU-047

    /** Criterio 2: cómo quedarían los grupos si se excluye el curso, sin guardar nada. */
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
     * Criterio 2: excluye un curso con justificación y renumera los grupos activos de la asignatura
     * desde 50 conservando su orden. Si tenía docente, se le quita (el frontend avisa antes).
     * Criterio 5: cada exclusión y cada renumeración van a la bitácora en la misma transacción.
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
     * Deshace una exclusión: el curso vuelve a PROGRAMADO, sin docente, con el siguiente número de la serie.
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
        anterior.put("fechaExclusion", objetivo.getFechaExclusion() != null ? objetivo.getFechaExclusion().toString() : null);

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

    // ------------------------------------------------------------------ apoyo

    private static String clave(String codeProgram, String codeCourse, Jornada jornada) {
        return codeProgram + "#" + codeCourse + "#" + jornada;
    }

    private static String clave(CursoProgramacion c) {
        return clave(c.getPrograma().getCodeProgram(), c.getAsignatura().getCodeCourse(), c.getJornada());
    }

    /**
     * Cursos que corresponden según las cohortes y sus mallas (sin electivas profesionales, HU-048).
     * Con {@code estricta} se exige que cada cohorte tenga asignaturas obligatorias (HU-045 criterio 4).
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
                        prog.getNameProgram() + " con resolución '" + cohorte.getNumeroResolucion() + "' no tiene asignaturas obligatorias en la malla.");
            }

            for (PensumAcademico pensum : obligatorias) {
                CursoOfertado ofertado = new CursoOfertado(cohorte, pensum);
                // Evitar duplicar materia en la misma jornada para el mismo programa
                oferta.putIfAbsent(ofertado.clave(), ofertado);
            }
        }
        return oferta;
    }

    /** HU-047 criterio 4: motivo por el que un curso activo requiere revisión, o null. */
    private String motivoRevision(CursoProgramacion c, Map<String, CursoOfertado> oferta, Set<String> cohortesConfirmadas) {
        if (c.isExcluido()) {
            return null;
        }
        if (Boolean.FALSE.equals(c.getAsignatura().getActivo())) {
            return REVISION_ASIGNATURA_INACTIVA;
        }
        if (!cohortesConfirmadas.contains(c.getPrograma().getCodeProgram() + "#" + c.getSemestre() + "#" + c.getJornada())) {
            return REVISION_SIN_COHORTE;
        }
        if (!oferta.containsKey(clave(c))) {
            return REVISION_FUERA_DE_MALLA;
        }
        return null;
    }

    /** Orden de la serie: primero por grupo actual; los que no tienen grupo, por programa y jornada. */
    private static final Comparator<CursoProgramacion> ORDEN_SERIE =
            Comparator.comparing(CursoProgramacion::getGrupo, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(c -> c.getPrograma().getCodeProgram())
                    .thenComparing(CursoProgramacion::getJornada);

    /** Numera consecutivo desde 50 los cursos activos de una asignatura; los excluidos quedan sin grupo. */
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

    /** Grupo que tendría cada curso activo si se excluye {@code cursoExcluir}; los excluidos quedan en null. */
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

    private ProgramacionDto.AjusteGruposResponse construirAjuste(CursoProgramacion curso, List<CursoProgramacion> cursos,
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
        CursoProgramacion curso = cursoRepository.findByIdWithDetails(cursoId)
                .orElseThrow(() -> new ResourceNotFoundException("Curso no encontrado con id " + cursoId));

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
            Docente docente = docenteRepository.findById(idDocente)
                    .orElseThrow(() -> new ResourceNotFoundException("Docente no encontrado con id " + idDocente));
            if (Boolean.FALSE.equals(docente.getEstado())) {
                throw new BadRequestException("El docente seleccionado está inactivo");
            }
            curso.setDocente(docente);
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
            curso.setEstado(CursoProgramacion.ESTADO_PROGRAMADO);
        }

        CursoProgramacion guardado = cursoRepository.save(curso);
        return toResponse(guardado, null);
    }

    @Transactional
    public ProgramacionDto.CursoResponse asignarDocentePorMateria(
            Long periodoId, String codeProgram, String codeCourse, Integer semestre, Long idDocente,
            boolean reemplazarDocente) {
        PeriodoAcademico periodo = periodoRepository.findById(periodoId)
                .orElseThrow(() -> new ResourceNotFoundException("Período no encontrado"));
        Programa programa = programaRepository.findById(codeProgram)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con código " + codeProgram));
        Asignatura asignatura = asignaturaRepository.findById(codeCourse)
                .orElseThrow(() -> new ResourceNotFoundException("Asignatura no encontrada con código " + codeCourse));

        List<CursoProgramacion> existentes = cursoRepository.findByPeriodoAndAsignatura(periodoId, codeCourse);
        CursoProgramacion curso = existentes.stream()
                .filter(c -> c.getPrograma().getCodeProgram().equalsIgnoreCase(codeProgram) && !c.isExcluido())
                .findFirst()
                .orElse(null);

        if (curso == null) {
            int grupo = existentes.isEmpty()
                    ? CursoProgramacion.GRUPO_INICIAL
                    : (existentes.size() + CursoProgramacion.GRUPO_INICIAL);

            String resolucion = pensumRepository.findByCodeProgramWithDetails(codeProgram).stream()
                    .filter(p -> p.getPrograma() != null)
                    .findFirst()
                    .map(p -> programa.getCodeProgram())
                    .orElse("VIGENTE");

            curso = CursoProgramacion.builder()
                    .periodo(periodo)
                    .programa(programa)
                    .asignatura(asignatura)
                    .semestre(semestre != null ? semestre : 1)
                    .jornada(programa.getJornada() != null ? programa.getJornada() : Jornada.DIURNA)
                    .numeroResolucion(resolucion)
                    .grupo(grupo)
                    .credits(asignatura.getCredits() != null ? asignatura.getCredits() : 0)
                    .nombreAsignatura(asignatura.getNameCourse())
                    .estado(CursoProgramacion.ESTADO_PROGRAMADO)
                    .build();
            curso = cursoRepository.save(curso);
        }

        return asignarDocente(curso.getId(), idDocente, reemplazarDocente);
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
                .semestre(c.getSemestre())
                .jornada(c.getJornada())
                .numeroResolucion(c.getNumeroResolucion())
                .grupo(c.getGrupo())
                .idDocente(idDocente)
                .nombreDocente(nombreDocente)
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
