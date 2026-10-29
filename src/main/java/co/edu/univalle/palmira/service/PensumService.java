package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.PensumItemDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Asignatura;
import co.edu.univalle.palmira.model.entity.PensumAcademico;
import co.edu.univalle.palmira.model.entity.PensumId;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.model.enums.TipoIngreso;
import co.edu.univalle.palmira.repository.AsignaturaRepository;
import co.edu.univalle.palmira.repository.PensumAcademicoRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PensumService {

    private static final String META_PREFIX = "META|";

    private final PensumAcademicoRepository pensumRepository;
    private final ProgramaRepository programaRepository;
    private final AsignaturaRepository asignaturaRepository;

    @Transactional(readOnly = true)
    public List<PensumItemDto.Response> getAll() {
        return pensumRepository.findAll()
                .stream()
                .filter(item -> Boolean.TRUE.equals(item.getVigente()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PensumItemDto.Response> getByProgram(String codeProgram) {
        String cleanCode = codeProgram.trim().toUpperCase();
        if (!programaRepository.existsById(cleanCode)) {
            throw new ResourceNotFoundException("Programa no encontrado con código: " + cleanCode);
        }
        return pensumRepository.findByCodeProgramWithDetails(cleanCode)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PensumItemDto.Response addCourseToPensum(PensumItemDto.AddRequest request) {
        String progCode = request.codeProgram().trim().toUpperCase();
        String courseCode = request.codeCourse().trim().toUpperCase();
        int semestre = request.semestre();

        if (semestre < 1 || semestre > 20) {
            throw new BadRequestException("El semestre debe ser un número entero entre 1 y 20");
        }

        Programa programa = programaRepository.findById(progCode)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con código: " + progCode));

        Asignatura asignatura = asignaturaRepository.findById(courseCode)
                .orElseThrow(() -> new ResourceNotFoundException("Asignatura no encontrada con código: " + courseCode));

        int version = currentVersion(progCode);
        PensumId id = new PensumId(progCode, courseCode, version);
        if (pensumRepository.existsById(id)) {
            throw new ConflictException(
                    "La asignatura " + courseCode + " ya está asignada al pensum del programa " + progCode, null);
        }

        PensumAcademico pensum = PensumAcademico.builder()
                .id(id)
                .programa(programa)
                .asignatura(asignatura)
                .semestre(semestre)
                .isElective(asignatura.getIsElective())
                .facultad(asignatura.getFacultad())
                .numeroResolucion(programa.getNumeroResolucion())
                .numeroSemestres(programa.getNumeroSemestre())
                .activo(true)
                .vigente(true)
                .editada(false)
                .build();

        PensumAcademico saved = pensumRepository.save(pensum);
        return toResponse(saved);
    }

    @Transactional
    public PensumItemDto.Response updateCourseSemester(PensumItemDto.UpdateSemesterRequest request) {
        String progCode = request.codeProgram().trim().toUpperCase();
        String courseCode = request.codeCourse().trim().toUpperCase();
        int semestre = request.semestre();

        if (semestre < 1 || semestre > 20) {
            throw new BadRequestException("El semestre debe ser un número entero entre 1 y 20");
        }

        int version = currentVersion(progCode);
        PensumId id = new PensumId(progCode, courseCode, version);
        PensumAcademico pensum = pensumRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Asignación no encontrada para programa " + progCode + " y asignatura " + courseCode));

        pensum.setSemestre(semestre);
        pensum.setEditada(true);
        pensum.setFechaEdicion(LocalDateTime.now());
        PensumAcademico updated = pensumRepository.save(pensum);
        return toResponse(updated);
    }

    @Transactional
    public PensumItemDto.Response cambiarEstado(String codeProgram, String codeCourse, Boolean activo) {
        String progCode = codeProgram.trim().toUpperCase();
        String courseCode = codeCourse.trim().toUpperCase();
        int version = currentVersion(progCode);
        PensumId id = new PensumId(progCode, courseCode, version);

        PensumAcademico pensum = pensumRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Asignación no encontrada para programa " + progCode + " y asignatura " + courseCode));

        pensum.setActivo(Boolean.TRUE.equals(activo));
        PensumAcademico updated = pensumRepository.save(pensum);
        return toResponse(updated);
    }

    @Transactional
    public void removeCourseFromPensum(String codeProgram, String codeCourse) {
        String progCode = codeProgram.trim().toUpperCase();
        String courseCode = codeCourse.trim().toUpperCase();
        int version = currentVersion(progCode);
        PensumId id = new PensumId(progCode, courseCode, version);

        if (!pensumRepository.existsById(id)) {
            throw new ResourceNotFoundException(
                    "Asignación no encontrada para programa " + progCode + " y asignatura " + courseCode);
        }

        pensumRepository.deleteById(id);
    }

    // HU-017: Registrar nueva resolución de programa (crea nueva versión con número de resolución y número de semestres, y pasa la anterior a inactiva)
    @Transactional
    public List<PensumItemDto.Response> saveVersion(PensumItemDto.SaveRequest request) {
        String programCode = normalize(request.codeProgram());
        Programa programa = programaRepository.findById(programCode)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado: " + programCode));

        validarSolicitudMalla(request);
        actualizarDatosPrograma(programa, request);

        List<PensumAcademico> lockedRows = pensumRepository.lockByProgram(programCode);
        int version = (lockedRows.stream()
                .map(PensumAcademico::getId)
                .map(PensumId::getVersion)
                .max(Integer::compareTo)
                .orElse(0)) + 1;

        LocalDateTime now = LocalDateTime.now();
        lockedRows.stream()
                .filter(row -> Boolean.TRUE.equals(row.getVigente()))
                .forEach(row -> {
                    row.setVigente(false);
                    row.setFechaDesactivacion(now);
                });

        String descClean = request.descripcion() != null ? request.descripcion().trim() : null;
        String resolucionClean = request.numeroResolucion() != null && !request.numeroResolucion().isBlank()
                ? request.numeroResolucion().trim()
                : programa.getNumeroResolucion();
        Integer numSemestres = request.numeroSemestre() != null
                ? request.numeroSemestre()
                : programa.getNumeroSemestre();

        List<PensumAcademico> saved = request.items().stream().map(item -> {
            String courseCode = normalize(item.codeCourse());
            Asignatura asignatura = resolveOrUpsertAsignatura(courseCode, item);
            String meta = buildMetadata(item, asignatura, descClean);
            return PensumAcademico.builder()
                    .id(new PensumId(programCode, courseCode, version))
                    .programa(programa)
                    .asignatura(asignatura)
                    .semestre(item.semestre())
                    .isElective(asignatura.getIsElective())
                    .facultad(meta)
                    .numeroResolucion(resolucionClean)
                    .numeroSemestres(numSemestres)
                    .activo(true)
                    .vigente(true)
                    .editada(false)
                    .fechaEdicion(null)
                    .build();
        }).toList();

        return pensumRepository.saveAll(saved).stream()
                .sorted(Comparator.comparing(PensumAcademico::getSemestre)
                        .thenComparing(p -> p.getAsignatura() != null ? p.getAsignatura().getNameCourse() : ""))
                .map(this::toResponse)
                .toList();
    }

    // HU-032: Editar la malla activa actual y dejar registro de que fue editada
    @Transactional
    public List<PensumItemDto.Response> updateActiveVersion(String codeProgram, PensumItemDto.SaveRequest request) {
        String programCode = normalize(codeProgram);
        if (!programCode.equalsIgnoreCase(normalize(request.codeProgram()))) {
            throw new BadRequestException("El programa de la ruta no coincide con el cuerpo de la solicitud");
        }

        Programa programa = programaRepository.findById(programCode)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado: " + programCode));

        List<PensumAcademico> lockedRows = pensumRepository.lockByProgram(programCode);
        List<PensumAcademico> activeRows = lockedRows.stream()
                .filter(row -> Boolean.TRUE.equals(row.getVigente()))
                .toList();

        if (activeRows.isEmpty()) {
            throw new ConflictException("Solo las mallas activas se pueden editar. Este programa no tiene malla activa.", null);
        }

        validarSolicitudMalla(request);

        if (!hayCambiosEnMalla(programa, activeRows, request)) {
            throw new BadRequestException("No hay cambios para guardar");
        }

        int activeVersion = activeRows.get(0).getId().getVersion();
        actualizarDatosPrograma(programa, request);

        Set<String> newCourseCodes = request.items().stream()
                .map(item -> normalize(item.codeCourse()))
                .collect(Collectors.toSet());

        // Eliminar asignaturas que fueron quitadas o cuyo código cambió en la malla activa
        List<PensumAcademico> toRemove = activeRows.stream()
                .filter(row -> !newCourseCodes.contains(normalize(row.getId().getCodeCourse())))
                .toList();
        if (!toRemove.isEmpty()) {
            pensumRepository.deleteAll(toRemove);
            pensumRepository.flush();
        }

        Map<String, PensumAcademico> existingMap = activeRows.stream()
                .filter(row -> newCourseCodes.contains(normalize(row.getId().getCodeCourse())))
                .collect(Collectors.toMap(
                        row -> normalize(row.getId().getCodeCourse()),
                        row -> row,
                        (first, ignored) -> first));

        String descClean = request.descripcion() != null
                ? request.descripcion().trim()
                : extractMetaField(activeRows.get(0).getFacultad(), "desc");
        String resolucionClean = request.numeroResolucion() != null && !request.numeroResolucion().isBlank()
                ? request.numeroResolucion().trim()
                : (activeRows.get(0).getNumeroResolucion() != null
                        ? activeRows.get(0).getNumeroResolucion()
                        : programa.getNumeroResolucion());
        Integer numSemestres = request.numeroSemestre() != null
                ? request.numeroSemestre()
                : (activeRows.get(0).getNumeroSemestres() != null
                        ? activeRows.get(0).getNumeroSemestres()
                        : programa.getNumeroSemestre());

        LocalDateTime editTime = LocalDateTime.now();

        List<PensumAcademico> toSave = new ArrayList<>();
        for (PensumItemDto.SaveItem item : request.items()) {
            String courseCode = normalize(item.codeCourse());
            Asignatura asignatura = resolveOrUpsertAsignatura(courseCode, item);
            String meta = buildMetadata(item, asignatura, descClean);

            PensumAcademico existing = existingMap.get(courseCode);
            if (existing != null) {
                existing.setSemestre(item.semestre());
                existing.setAsignatura(asignatura);
                existing.setIsElective(asignatura.getIsElective());
                existing.setFacultad(meta);
                existing.setNumeroResolucion(resolucionClean);
                existing.setNumeroSemestres(numSemestres);
                existing.setActivo(true);
                existing.setVigente(true);
                existing.setEditada(true);
                existing.setFechaEdicion(editTime);
                toSave.add(existing);
            } else {
                toSave.add(PensumAcademico.builder()
                        .id(new PensumId(programCode, courseCode, activeVersion))
                        .programa(programa)
                        .asignatura(asignatura)
                        .semestre(item.semestre())
                        .isElective(asignatura.getIsElective())
                        .facultad(meta)
                        .numeroResolucion(resolucionClean)
                        .numeroSemestres(numSemestres)
                        .activo(true)
                        .vigente(true)
                        .editada(true)
                        .fechaEdicion(editTime)
                        .build());
            }
        }

        return pensumRepository.saveAll(toSave).stream()
                .sorted(Comparator.comparing(PensumAcademico::getSemestre)
                        .thenComparing(p -> p.getAsignatura() != null ? p.getAsignatura().getNameCourse() : ""))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PensumItemDto.HistorySummary> getHistory(String codeProgram) {
        String normalized = normalize(codeProgram);
        return pensumRepository.findByIdCodeProgramAndVigenteFalseOrderByFechaDesactivacionDesc(normalized)
                .stream()
                .collect(Collectors.groupingBy(
                        row -> row.getId().getVersion(),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .values().stream()
                .map(rows -> {
                    PensumAcademico first = rows.get(0);
                    boolean anyEdited = rows.stream().anyMatch(r -> Boolean.TRUE.equals(r.getEditada()));
                    LocalDateTime lastEdit = rows.stream()
                            .map(PensumAcademico::getFechaEdicion)
                            .filter(Objects::nonNull)
                            .max(LocalDateTime::compareTo)
                            .orElse(null);
                    return new PensumItemDto.HistorySummary(
                            first.getId().getVersion(),
                            first.getFechaDesactivacion(),
                            rows.size(),
                            first.getNumeroResolucion(),
                            first.getNumeroSemestres(),
                            anyEdited,
                            lastEdit);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PensumItemDto.Response> getVersion(String codeProgram, Integer version) {
        return pensumRepository.findByIdCodeProgramAndIdVersion(normalize(codeProgram), version)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deactivateCurrent(String codeProgram) {
        String normalized = normalize(codeProgram);
        List<PensumAcademico> rows = pensumRepository.lockByProgram(normalized).stream()
                .filter(row -> Boolean.TRUE.equals(row.getVigente()))
                .toList();
        if (rows.isEmpty()) {
            throw new ConflictException("El programa no tiene una malla vigente", null);
        }
        LocalDateTime now = LocalDateTime.now();
        rows.forEach(row -> {
            row.setVigente(false);
            row.setFechaDesactivacion(now);
        });
    }

    @Transactional
    public List<PensumItemDto.Response> reactivate(String codeProgram, Integer version) {
        String normalized = normalize(codeProgram);
        List<PensumAcademico> allRows = pensumRepository.lockByProgram(normalized);
        List<PensumAcademico> target = allRows.stream()
                .filter(row -> row.getId().getVersion().equals(version))
                .toList();
        if (target.isEmpty()) {
            throw new ResourceNotFoundException("Versión de malla no encontrada");
        }
        if (target.stream().allMatch(row -> Boolean.TRUE.equals(row.getVigente()))) {
            throw new ConflictException("La malla ya está vigente", null);
        }
        LocalDateTime now = LocalDateTime.now();
        allRows.stream().filter(row -> Boolean.TRUE.equals(row.getVigente())).forEach(row -> {
            row.setVigente(false);
            row.setFechaDesactivacion(now);
        });
        target.forEach(row -> {
            row.setVigente(true);
            row.setFechaDesactivacion(null);
            row.setFechaReactivacion(now);
        });
        return target.stream().map(this::toResponse).toList();
    }

    private void validarSolicitudMalla(PensumItemDto.SaveRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("Se necesita al menos un semestre con una asignatura");
        }

        Set<String> seenCodes = new HashSet<>();

        for (PensumItemDto.SaveItem item : request.items()) {
            if (item.codeCourse() == null || item.codeCourse().trim().isEmpty()) {
                throw new BadRequestException("El código de la asignatura es obligatorio");
            }
            String code = normalize(item.codeCourse());
            if (!seenCodes.add(code)) {
                throw new BadRequestException("El código de la asignatura '" + code + "' está duplicado en la malla");
            }

            if (item.nameCourse() != null) {
                if (item.nameCourse().trim().isEmpty()) {
                    throw new BadRequestException("El nombre de la asignatura " + code + " es obligatorio");
                }
                boolean isSpecialElectiveCode = code.matches("^ELE-PROF-[A-Z0-9-]+$") || code.matches("^ELE-[A-Z0-9-]+$");
                if (!isSpecialElectiveCode && (code.length() > 8 || !code.matches("^[A-Z0-9]+$"))) {
                    throw new BadRequestException("El código de la asignatura '" + code
                            + "' debe ser alfanumérico y no mayor a 8 caracteres (excepto electivas profesionales ELE-PROF-I, II, III...)");
                }
            }

            if (item.semestre() == null || item.semestre() < 1 || item.semestre() > 20) {
                throw new BadRequestException("El número de semestre debe estar entre 1 y 20");
            }

            if (item.credits() != null && (item.credits() < 0 || item.credits() > 9)) {
                throw new BadRequestException(
                        "Los créditos de la asignatura " + code + " deben ser de 1 dígito (0 a 9)");
            }
            if (item.horasClase() != null && (item.horasClase() < 0 || item.horasClase() > 9)) {
                throw new BadRequestException(
                        "Las horas de clase de la asignatura " + code + " deben ser de 1 dígito (0 a 9)");
            }
            if (item.horasIndependiente() != null
                    && (item.horasIndependiente() < 0 || item.horasIndependiente() > 99)) {
                throw new BadRequestException(
                        "Las horas de estudio independiente de " + code + " deben ser de máximo 2 dígitos (0 a 99)");
            }
        }
    }

    private boolean hayCambiosEnMalla(Programa programa, List<PensumAcademico> activeRows,
            PensumItemDto.SaveRequest request) {
        if (request.nameProgram() != null && !request.nameProgram().trim().isEmpty()
                && !request.nameProgram().trim().equals(programa.getNameProgram())) {
            return true;
        }
        if (request.jornada() != null && !request.jornada().trim().isEmpty()
                && !request.jornada().trim()
                        .equalsIgnoreCase(programa.getJornada() != null ? programa.getJornada().name() : "DIURNA")) {
            return true;
        }
        if (request.periodoOferta() != null && !request.periodoOferta().trim().isEmpty()) {
            String currentOferta = programa.getApertura() != null ? programa.getApertura()
                    : (programa.getIngreso() != null ? programa.getIngreso().name() : "SEMESTRAL");
            if (!request.periodoOferta().trim().equalsIgnoreCase(currentOferta)) {
                return true;
            }
        }
        if (request.numeroResolucion() != null && !request.numeroResolucion().trim().isEmpty()) {
            String currentRes = !activeRows.isEmpty() && activeRows.get(0).getNumeroResolucion() != null
                    ? activeRows.get(0).getNumeroResolucion()
                    : Optional.ofNullable(programa.getNumeroResolucion()).orElse("");
            if (!request.numeroResolucion().trim().equalsIgnoreCase(currentRes)) {
                return true;
            }
        }
        if (request.numeroSemestre() != null) {
            Integer currentSem = !activeRows.isEmpty() && activeRows.get(0).getNumeroSemestres() != null
                    ? activeRows.get(0).getNumeroSemestres()
                    : programa.getNumeroSemestre();
            if (!Objects.equals(request.numeroSemestre(), currentSem)) {
                return true;
            }
        }

        String currentDesc = activeRows.isEmpty() ? ""
                : Optional.ofNullable(extractMetaField(activeRows.get(0).getFacultad(), "desc")).orElse("");
        if (request.descripcion() != null && !request.descripcion().trim().equals(currentDesc)) {
            return true;
        }

        if (activeRows.size() != request.items().size()) {
            return true;
        }

        Map<String, PensumAcademico> activeByCode = activeRows.stream()
                .collect(Collectors.toMap(
                        r -> normalize(r.getId().getCodeCourse()),
                        r -> r,
                        (a, b) -> a));

        for (PensumItemDto.SaveItem item : request.items()) {
            String code = normalize(item.codeCourse());
            PensumAcademico existing = activeByCode.get(code);
            if (existing == null) {
                return true;
            }
            if (!Objects.equals(existing.getSemestre(), item.semestre())) {
                return true;
            }
            Asignatura a = existing.getAsignatura();
            if (item.nameCourse() != null && a != null && !item.nameCourse().trim().equals(a.getNameCourse())) {
                return true;
            }
            if (item.credits() != null && a != null && !Objects.equals(item.credits(), a.getCredits())) {
                return true;
            }
            Integer existingHc = parseIntSafe(extractMetaField(existing.getFacultad(), "hc"), null);
            if (item.horasClase() != null && !Objects.equals(item.horasClase(), existingHc)) {
                return true;
            }
            Integer existingHi = parseIntSafe(extractMetaField(existing.getFacultad(), "hi"), null);
            if (item.horasIndependiente() != null && !Objects.equals(item.horasIndependiente(), existingHi)) {
                return true;
            }
            String existingTipo = extractMetaField(existing.getFacultad(), "tipo");
            if (existingTipo == null && a != null) {
                existingTipo = inferTipoFromAsignatura(a);
            }
            if (item.tipoAsignatura() != null
                    && !item.tipoAsignatura().trim().equalsIgnoreCase(Optional.ofNullable(existingTipo).orElse(""))) {
                return true;
            }
        }

        return false;
    }

    private void actualizarDatosPrograma(Programa programa, PensumItemDto.SaveRequest request) {
        boolean modified = false;
        if (request.nameProgram() != null && !request.nameProgram().trim().isEmpty()) {
            programa.setNameProgram(request.nameProgram().trim());
            modified = true;
        }
        if (request.numeroResolucion() != null && !request.numeroResolucion().trim().isEmpty()) {
            programa.setNumeroResolucion(request.numeroResolucion().trim());
            modified = true;
        }
        if (request.jornada() != null && !request.jornada().trim().isEmpty()) {
            try {
                programa.setJornada(Jornada.valueOf(request.jornada().trim().toUpperCase()));
                modified = true;
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (request.periodoOferta() != null && !request.periodoOferta().trim().isEmpty()) {
            String oferta = request.periodoOferta().trim().toUpperCase();
            if ("SEMESTRAL".equals(oferta) || "ANUAL".equals(oferta)) {
                programa.setApertura(oferta);
                programa.setIngreso(TipoIngreso.valueOf(oferta));
                modified = true;
            }
        }
        int maxSem = request.items().stream()
                .map(PensumItemDto.SaveItem::semestre)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(programa.getNumeroSemestre() != null ? programa.getNumeroSemestre() : 10);

        if (request.numeroSemestre() != null && request.numeroSemestre() >= maxSem) {
            maxSem = request.numeroSemestre();
        }

        if (programa.getJornada() == Jornada.NOCTURNA) {
            programa.setSemestresNocturna(maxSem);
            programa.setNumeroSemestre(maxSem);
            modified = true;
        } else if (request.nameProgram() != null || request.numeroSemestre() != null
                || (programa.getNumeroSemestre() != null && maxSem != programa.getNumeroSemestre())) {
            programa.setNumeroSemestre(maxSem);
            modified = true;
        }

        if (modified) {
            programaRepository.save(programa);
        }
    }

    private Asignatura resolveOrUpsertAsignatura(String courseCode, PensumItemDto.SaveItem item) {
        Optional<Asignatura> existingOpt = asignaturaRepository.findById(courseCode);
        String tipo = item.tipoAsignatura() != null ? item.tipoAsignatura().trim().toUpperCase() : null;

        boolean esBasica = "BASICA".equals(tipo) || "ASIGNATURA BASICA".equals(tipo)
                || "ASIGNATURA BÁSICA".equals(tipo);
        boolean esComplementaria = "ELECTIVA_COMPLEMENTARIA".equals(tipo) || "ELECTIVA COMPLEMENTARIA".equals(tipo);
        boolean isElective = esComplementaria || "EXTRACURRICULAR".equals(tipo) || "ACT. EXTRACURRICULAR".equals(tipo);

        if (existingOpt.isPresent()) {
            Asignatura asignatura = existingOpt.get();
            boolean changed = false;
            if (item.nameCourse() != null && !item.nameCourse().trim().isEmpty()
                    && !item.nameCourse().trim().equals(asignatura.getNameCourse())) {
                asignatura.setNameCourse(item.nameCourse().trim());
                changed = true;
            }
            if (item.credits() != null && !Objects.equals(item.credits(), asignatura.getCredits())) {
                asignatura.setCredits(Math.max(0, item.credits()));
                changed = true;
            }
            if (tipo != null) {
                asignatura.setEsBasica(esBasica);
                asignatura.setEsComplementaria(esComplementaria);
                asignatura.setIsElective(isElective);
                changed = true;
            }
            return changed ? asignaturaRepository.save(asignatura) : asignatura;
        }

        if (item.nameCourse() == null || item.nameCourse().trim().isEmpty()) {
            throw new ResourceNotFoundException("Asignatura no encontrada: " + courseCode);
        }

        Asignatura nueva = Asignatura.builder()
                .codeCourse(courseCode)
                .nameCourse(item.nameCourse().trim())
                .credits(item.credits() != null ? Math.max(0, item.credits()) : 0)
                .esBasica(esBasica)
                .esComplementaria(esComplementaria)
                .isElective(isElective)
                .activo(true)
                .build();
        return asignaturaRepository.save(nueva);
    }

    private String buildMetadata(PensumItemDto.SaveItem item, Asignatura asignatura, String descripcion) {
        if (item.horasClase() == null && item.horasIndependiente() == null && item.tipoAsignatura() == null
                && (descripcion == null || descripcion.isEmpty())) {
            return asignatura.getFacultad();
        }
        int hc = item.horasClase() != null ? item.horasClase() : 3;
        int hi = item.horasIndependiente() != null ? item.horasIndependiente() : 6;
        String tipo = item.tipoAsignatura() != null && !item.tipoAsignatura().isBlank()
                ? item.tipoAsignatura().trim().replace("|", "").replace("=", "")
                : inferTipoFromAsignatura(asignatura);
        String descSafe = descripcion != null
                ? descripcion.replace("|", " ").replace("=", " ").replace("\n", " ").replace("\r", " ").trim()
                : "";
        String prefix = META_PREFIX + "hc=" + hc + "|hi=" + hi + "|tipo=" + tipo + "|desc=";
        int maxDescLen = Math.max(0, 148 - prefix.length());
        if (descSafe.length() > maxDescLen) {
            descSafe = descSafe.substring(0, maxDescLen);
        }
        return prefix + descSafe;
    }

    private String inferTipoFromAsignatura(Asignatura a) {
        if (a == null)
            return "PROFESIONAL";
        if (Boolean.TRUE.equals(a.getEsBasica()))
            return "BASICA";
        if (Boolean.TRUE.equals(a.getEsComplementaria()))
            return "ELECTIVA_COMPLEMENTARIA";
        if (Boolean.TRUE.equals(a.getIsElective()))
            return "EXTRACURRICULAR";
        return "PROFESIONAL";
    }

    private String extractMetaField(String rawFacultad, String key) {
        if (rawFacultad == null || !rawFacultad.startsWith(META_PREFIX)) {
            return null;
        }
        String body = rawFacultad.substring(META_PREFIX.length());
        String[] parts = body.split("\\|");
        String prefix = key + "=";
        for (String part : parts) {
            if (part.startsWith(prefix)) {
                return part.substring(prefix.length());
            }
        }
        return null;
    }

    private Integer parseIntSafe(String val, Integer fallback) {
        if (val == null || val.isBlank())
            return fallback;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int currentVersion(String codeProgram) {
        return pensumRepository.findByIdCodeProgramAndVigenteTrue(codeProgram).stream()
                .findFirst()
                .map(row -> row.getId().getVersion())
                .orElseGet(() -> Math.max(1, pensumRepository.findMaxVersion(codeProgram) + 1));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private PensumItemDto.Response toResponse(PensumAcademico p) {
        Asignatura a = p.getAsignatura();
        Programa prog = p.getPrograma();
        String rawMeta = p.getFacultad();
        boolean hasMeta = rawMeta != null && rawMeta.startsWith(META_PREFIX);

        Integer credits = a != null && a.getCredits() != null ? a.getCredits() : 0;
        Integer hc = hasMeta ? parseIntSafe(extractMetaField(rawMeta, "hc"), null) : null;
        Integer hi = hasMeta ? parseIntSafe(extractMetaField(rawMeta, "hi"), null) : null;
        String tipo = hasMeta ? extractMetaField(rawMeta, "tipo") : inferTipoFromAsignatura(a);
        String desc = hasMeta ? extractMetaField(rawMeta, "desc") : null;
        String facultadClean = hasMeta ? (a != null ? a.getFacultad() : null)
                : (a != null ? a.getFacultad() : p.getFacultad());

        String jornada = prog != null && prog.getJornada() != null ? prog.getJornada().name() : null;
        String periodoOferta = prog != null
                ? (prog.getApertura() != null ? prog.getApertura()
                        : (prog.getIngreso() != null ? prog.getIngreso().name() : null))
                : null;
        String numeroResolucion = p.getNumeroResolucion() != null
                ? p.getNumeroResolucion()
                : (prog != null ? prog.getNumeroResolucion() : null);
        Integer numeroSemestres = p.getNumeroSemestres() != null
                ? p.getNumeroSemestres()
                : (prog != null ? prog.getNumeroSemestre() : null);

        return new PensumItemDto.Response(
                p.getId().getCodeProgram(),
                p.getId().getCodeCourse(),
                a != null ? a.getNameCourse() : p.getId().getCodeCourse(),
                credits,
                p.getSemestre(),
                a != null ? a.getIsElective() : p.getIsElective(),
                a != null ? a.getEsComplementaria() : false,
                a != null ? a.getEsBasica() : false,
                facultadClean,
                p.getActivo() != null ? p.getActivo() : true,
                p.getId().getVersion(),
                p.getVigente() != null ? p.getVigente() : true,
                hc,
                hi,
                tipo,
                desc,
                jornada,
                periodoOferta,
                numeroResolucion,
                numeroSemestres,
                p.getEditada() != null ? p.getEditada() : false,
                p.getFechaEdicion());
    }
}
