package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.DisponibilidadDocenteDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.AccesoExtraordinarioDocente;
import co.edu.univalle.palmira.model.entity.DisponibilidadDocente;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.repository.DisponibilidadDocenteRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DisponibilidadDocenteService {
    private final DisponibilidadDocenteRepository disponibilidadRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final DocenteRepository docenteRepository;
    private final AccesoExtraordinarioService accesoExtraordinarioService;

    private static final LocalTime HORA_MINIMA = LocalTime.of(7, 0);
    private static final LocalTime HORA_MAXIMA = LocalTime.of(21, 0);

    // Prueba de usabilidad (2026-11-10): cada intervalo dura mínimo 1 hora y el
    // sábado no se registra disponibilidad de 19:00 a 21:00 (HU-007 y HU-051).
    static final int DURACION_MINIMA_MINUTOS = 60;
    static final int SABADO = 6;
    static final LocalTime SABADO_HORA_MAXIMA = LocalTime.of(19, 0);
    static final String MENSAJE_FUERA_DE_RANGO = "Hora fuera del rango permitido";
    static final String MENSAJE_MINIMO_UNA_HORA =
            "No se puede seleccionar 30 minutos, el registro mínimo es 1 hora";

    /**
     * Consulta la disponibilidad del docente asociado al correo autenticado.
     * Se conserva este flujo para el autoservicio del docente.
     */
    @Transactional(readOnly = true)
    public DisponibilidadDocenteDto.Response consultar(String correoInstitucional) {
        return consultarDocente(obtenerDocenteActivo(correoInstitucional));
    }

    /**
     * Consulta la disponibilidad de un docente seleccionado por un administrador.
     * El ID recibido corresponde a docentes.id, nunca a usuarios.id.
     */
    @Transactional(readOnly = true)
    public DisponibilidadDocenteDto.Response consultarPorDocenteId(Long docenteId) {
        return consultarDocente(obtenerDocenteActivoPorId(docenteId));
    }

    @Transactional(readOnly = true)
    public java.util.Map<Long, List<DisponibilidadDocenteDto.Item>> consultarPorPeriodo(Long periodoId) {
        List<DisponibilidadDocente> lista = disponibilidadRepository.findAllByPeriodoIdOrderByDiaAscHoraInicioAsc(periodoId);
        return lista.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        d -> d.getDocente().getId(),
                        java.util.stream.Collectors.mapping(
                                d -> new DisponibilidadDocenteDto.Item(d.getId(), d.getDia(), d.getHoraInicio(), d.getHoraFin()),
                                java.util.stream.Collectors.toList()
                        )
                ));
    }

    @Transactional
    public DisponibilidadDocenteDto.Item registrar(
            String correoInstitucional, DisponibilidadDocenteDto.Request request) {
        return registrarParaDocente(obtenerDocenteActivo(correoInstitucional), request);
    }

    /** Registra disponibilidad para el docente seleccionado por un administrador. */
    @Transactional
    public DisponibilidadDocenteDto.Item registrarPorDocenteId(
            Long docenteId, DisponibilidadDocenteDto.Request request) {
        return registrarParaDocente(obtenerDocenteActivoPorId(docenteId), request);
    }

    @Transactional
    public List<DisponibilidadDocenteDto.Item> actualizarTodo(
            String correoInstitucional, List<DisponibilidadDocenteDto.Request> requests) {
        return actualizarDisponibilidadDeDocente(obtenerDocenteActivo(correoInstitucional), requests);
    }

    /** Actualiza todos los intervalos del docente seleccionado por un administrador. */
    @Transactional
    public List<DisponibilidadDocenteDto.Item> actualizarTodoPorDocenteId(
            Long docenteId, List<DisponibilidadDocenteDto.Request> requests) {
        return actualizarDisponibilidadDeDocente(obtenerDocenteActivoPorId(docenteId), requests);
    }

    /*
     * Los métodos siguientes contienen la lógica de negocio compartida por ambos
     * flujos. Así las validaciones no se duplican ni cambian para el docente.
     */
    private DisponibilidadDocenteDto.Response consultarDocente(Docente docente) {
        PeriodoAcademico periodo = periodoRepository.findFirstByVigenteTrue()
                .filter(this::esPeriodoActivo)
                .orElse(null);
        if (periodo == null) {
            return new DisponibilidadDocenteDto.Response(
                    "SIN_PERIODO", "No hay un período académico vigente", List.of());
        }

        List<DisponibilidadDocenteDto.Item> horarios = listar(docente.getId(), periodo.getId());
        Instant ahora = Instant.now();
        if (estaEnPlazo(periodo, ahora)) {
            return new DisponibilidadDocenteDto.Response(
                    "HABILITADO", "Registrar disponibilidad", horarios,
                    periodo.getPlazoDocenteInicio(), periodo.getPlazoDocenteFin());
        }
        // Ventana general cerrada: puede estar habilitado por un acceso extraordinario del administrador.
        Optional<AccesoExtraordinarioDocente> extraordinario =
                accesoExtraordinarioService.vigente(docente.getId(), ahora);
        if (extraordinario.isPresent()) {
            return new DisponibilidadDocenteDto.Response(
                    "HABILITADO", "Acceso extraordinario habilitado", horarios,
                    periodo.getPlazoDocenteInicio(), periodo.getPlazoDocenteFin(),
                    extraordinario.get().getFechaFin());
        }
        return new DisponibilidadDocenteDto.Response(
                "FUERA_DE_PLAZO", "Fuera del período habilitado", horarios,
                periodo.getPlazoDocenteInicio(), periodo.getPlazoDocenteFin());
    }

    private DisponibilidadDocenteDto.Item registrarParaDocente(
            Docente docente, DisponibilidadDocenteDto.Request request) {
        PeriodoAcademico periodo = periodoRepository.findFirstByVigenteTrue()
                .filter(this::esPeriodoActivo)
                .orElseThrow(() -> new BadRequestException("No hay un período académico vigente"));

        if (!estaHabilitado(periodo, docente, Instant.now())) {
            throw new BadRequestException("Fuera del período habilitado");
        }

        validarHorario(request);

        List<DisponibilidadDocente> cruces = disponibilidadRepository.findCruces(
                docente.getId(), periodo.getId(), request.dia(), request.horaInicio(), request.horaFin());
        if (!cruces.isEmpty()) {
            DisponibilidadDocente existente = cruces.get(0);
            throw new BadRequestException("El horario se cruza con otro ya registrado ("
                    + nombreDia(existente.getDia()) + " " + existente.getHoraInicio()
                    + " a " + existente.getHoraFin() + ")");
        }

        DisponibilidadDocente guardado = disponibilidadRepository.save(DisponibilidadDocente.builder()
                .docente(docente)
                .periodo(periodo)
                .dia(request.dia())
                .horaInicio(request.horaInicio())
                .horaFin(request.horaFin())
                .build());
        return toItem(guardado);
    }

    private List<DisponibilidadDocenteDto.Item> actualizarDisponibilidadDeDocente(
            Docente docente, List<DisponibilidadDocenteDto.Request> requests) {
        PeriodoAcademico periodo = periodoRepository.findFirstByVigenteTrue()
                .filter(this::esPeriodoActivo)
                .orElseThrow(() -> new BadRequestException("No hay un período académico vigente"));

        if (!estaHabilitado(periodo, docente, Instant.now())) {
            throw new BadRequestException("Fuera del período habilitado");
        }

        List<DisponibilidadDocenteDto.Request> lista = requests == null ? List.of() : requests;
        for (int i = 0; i < lista.size(); i++) {
            DisponibilidadDocenteDto.Request req = lista.get(i);
            validarHorario(req);
            for (int j = i + 1; j < lista.size(); j++) {
                DisponibilidadDocenteDto.Request otro = lista.get(j);
                if (otro != null && req.dia().equals(otro.dia())
                        && req.horaInicio().isBefore(otro.horaFin())
                        && req.horaFin().isAfter(otro.horaInicio())) {
                    throw new BadRequestException("El horario se cruza con otro intervalo enviado ("
                            + nombreDia(req.dia()) + ")");
                }
            }
        }

        disponibilidadRepository.deleteAllByDocenteIdAndPeriodoId(docente.getId(), periodo.getId());
        disponibilidadRepository.flush();

        List<DisponibilidadDocente> entidades = lista.stream()
                .map(req -> DisponibilidadDocente.builder()
                        .docente(docente)
                        .periodo(periodo)
                        .dia(req.dia())
                        .horaInicio(req.horaInicio())
                        .horaFin(req.horaFin())
                        .build())
                .toList();

        return disponibilidadRepository.saveAll(entidades).stream()
                .map(this::toItem)
                .toList();
    }

    private Docente obtenerDocenteActivo(String correoInstitucional) {
        if (correoInstitucional == null || correoInstitucional.isBlank()) {
            throw new ResourceNotFoundException("No se pudo identificar al docente autenticado");
        }
        Docente docente = docenteRepository.findByCorreoInstitucionalWithDetails(
                        correoInstitucional.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Docente no encontrado"));
        if (Boolean.FALSE.equals(docente.getEstado())) {
            throw new ResourceNotFoundException("Docente no encontrado");
        }
        return docente;
    }

    private Docente obtenerDocenteActivoPorId(Long docenteId) {
        if (docenteId == null || docenteId <= 0) {
            throw new ResourceNotFoundException("Docente no encontrado");
        }
        Docente docente = docenteRepository.findById(docenteId)
                .orElseThrow(() -> new ResourceNotFoundException("Docente no encontrado"));
        if (Boolean.FALSE.equals(docente.getEstado())) {
            throw new ResourceNotFoundException("Docente no encontrado");
        }
        return docente;
    }

    private boolean esPeriodoActivo(PeriodoAcademico periodo) {
        if (periodo == null) {
            return false;
        }
        String estado = periodo.getEstado();
        if (estado != null && !estado.isBlank()) {
            String norm = estado.trim().toUpperCase(Locale.ROOT);
            if ("DESACTIVADO".equals(norm) || "HISTORIAL".equals(norm) || "HISTORICO".equals(norm)) {
                return false;
            }
            return "VIGENTE".equals(norm) || "ACTIVO".equals(norm);
        }
        return true;
    }

    /** Ventana general del período, o un acceso extraordinario vigente de este docente. */
    private boolean estaHabilitado(PeriodoAcademico periodo, Docente docente, Instant ahora) {
        return estaEnPlazo(periodo, ahora)
                || accesoExtraordinarioService.vigente(docente.getId(), ahora).isPresent();
    }

    private boolean estaEnPlazo(PeriodoAcademico periodo, Instant ahora) {
        if (periodo.getPlazoDocenteInicio() == null || periodo.getPlazoDocenteFin() == null) {
            return false;
        }
        return !ahora.isBefore(periodo.getPlazoDocenteInicio())
                && !ahora.isAfter(periodo.getPlazoDocenteFin());
    }

    private void validarHorario(DisponibilidadDocenteDto.Request request) {
        if (request == null || request.dia() == null
                || request.horaInicio() == null || request.horaFin() == null) {
            throw new BadRequestException("El día, la hora de inicio y la hora de fin son obligatorios");
        }
        if (request.dia() < 1 || request.dia() > 6) {
            throw new BadRequestException("El día debe estar entre lunes y sábado");
        }
        if (!request.horaFin().isAfter(request.horaInicio())) {
            throw new BadRequestException("La hora de fin debe ser posterior a la hora de inicio");
        }
        if (request.horaInicio().isBefore(HORA_MINIMA) || request.horaFin().isAfter(HORA_MAXIMA)) {
            throw new BadRequestException("El horario debe estar entre las 07:00 y las 21:00");
        }
        // Prueba de usabilidad (2026-11-10): van después del rango general para
        // conservar su mensaje cuando el horario se sale de 07:00 a 21:00.
        if (request.dia() == SABADO && request.horaFin().isAfter(SABADO_HORA_MAXIMA)) {
            throw new BadRequestException(MENSAJE_FUERA_DE_RANGO);
        }
        if (Duration.between(request.horaInicio(), request.horaFin()).toMinutes() < DURACION_MINIMA_MINUTOS) {
            throw new BadRequestException(MENSAJE_MINIMO_UNA_HORA);
        }
    }

    private List<DisponibilidadDocenteDto.Item> listar(Long docenteId, Long periodoId) {
        return disponibilidadRepository
                .findAllByDocenteIdAndPeriodoIdOrderByDiaAscHoraInicioAsc(docenteId, periodoId)
                .stream().map(this::toItem).toList();
    }

    private DisponibilidadDocenteDto.Item toItem(DisponibilidadDocente horario) {
        return new DisponibilidadDocenteDto.Item(horario.getId(), horario.getDia(),
                horario.getHoraInicio(), horario.getHoraFin());
    }

    private String nombreDia(int dia) {
        return List.of("lunes", "martes", "miércoles", "jueves", "viernes", "sábado").get(dia - 1);
    }
}