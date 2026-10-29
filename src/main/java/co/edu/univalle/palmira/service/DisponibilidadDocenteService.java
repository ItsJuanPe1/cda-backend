package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.DisponibilidadDocenteDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.DisponibilidadDocente;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.repository.DisponibilidadDocenteRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DisponibilidadDocenteService {
    private final DisponibilidadDocenteRepository disponibilidadRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final DocenteRepository docenteRepository;

    private static final LocalTime HORA_MINIMA = LocalTime.of(7, 0);
    private static final LocalTime HORA_MAXIMA = LocalTime.of(21, 0);

    /**
     * Devuelve el estado del registro de disponibilidad y los rangos del docente
     * para el período académico vigente. Se identifica por el correo firmado en
     * el JWT, no por sub, porque un coordinador también puede tener perfil docente.
     */
    @Transactional(readOnly = true)
    public DisponibilidadDocenteDto.Response consultar(String correoInstitucional) {
        Docente docente = obtenerDocenteActivo(correoInstitucional);
        PeriodoAcademico periodo = periodoRepository.findFirstByVigenteTrue()
                .filter(this::esPeriodoActivo)
                .orElse(null);
        if (periodo == null) {
            return new DisponibilidadDocenteDto.Response(
                    "SIN_PERIODO", "No hay un período académico vigente", List.of());
        }

        List<DisponibilidadDocenteDto.Item> horarios = listar(docente.getId(), periodo.getId());
        boolean abierto = estaEnPlazo(periodo, Instant.now());
        return new DisponibilidadDocenteDto.Response(
                abierto ? "HABILITADO" : "FUERA_DE_PLAZO",
                abierto ? "Registrar disponibilidad" : "Fuera del período habilitado",
                horarios,
                periodo.getPlazoDocenteInicio(),
                periodo.getPlazoDocenteFin());
    }

    @Transactional
    public DisponibilidadDocenteDto.Item registrar(
            String correoInstitucional, DisponibilidadDocenteDto.Request request) {
        Docente docente = obtenerDocenteActivo(correoInstitucional);
        PeriodoAcademico periodo = periodoRepository.findFirstByVigenteTrue()
                .filter(this::esPeriodoActivo)
                .orElseThrow(() -> new BadRequestException("No hay un período académico vigente"));

        if (!estaEnPlazo(periodo, Instant.now())) {
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

    @Transactional
    public List<DisponibilidadDocenteDto.Item> actualizarTodo(
            String correoInstitucional, List<DisponibilidadDocenteDto.Request> requests) {
        Docente docente = obtenerDocenteActivo(correoInstitucional);
        PeriodoAcademico periodo = periodoRepository.findFirstByVigenteTrue()
                .filter(this::esPeriodoActivo)
                .orElseThrow(() -> new BadRequestException("No hay un período académico vigente"));

        if (!estaEnPlazo(periodo, Instant.now())) {
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
