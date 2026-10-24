package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.DisponibilidadDocenteDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.DisponibilidadDocente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.entity.Usuario;
import co.edu.univalle.palmira.repository.DisponibilidadDocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import co.edu.univalle.palmira.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DisponibilidadDocenteService {
    private final DisponibilidadDocenteRepository disponibilidadRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${disponibilidad.hora-inicio:06:00}")
    private LocalTime horaMinima;

    @Value("${disponibilidad.hora-fin:22:00}")
    private LocalTime horaMaxima;

    @Transactional(readOnly = true)
    public DisponibilidadDocenteDto.Response consultar(Integer usuarioId) {
        PeriodoAcademico periodo = periodoRepository.findFirstByVigenteTrue().orElse(null);
        if (periodo == null) {
            return new DisponibilidadDocenteDto.Response("SIN_PERIODO",
                    "No hay un período académico vigente", List.of());
        }
        List<DisponibilidadDocenteDto.Item> horarios = listar(usuarioId, periodo.getId());
        Instant ahora = Instant.now();
        boolean abierto = !ahora.isBefore(periodo.getPlazoDocenteInicio())
                && !ahora.isAfter(periodo.getPlazoDocenteFin());
        return new DisponibilidadDocenteDto.Response(abierto ? "HABILITADO" : "FUERA_DE_PLAZO",
                abierto ? "Registrar disponibilidad" : "Fuera del período habilitado", horarios);
    }

    @Transactional
    public DisponibilidadDocenteDto.Item registrar(Integer usuarioId, DisponibilidadDocenteDto.Request request) {
        PeriodoAcademico periodo = periodoRepository.findFirstByVigenteTrue()
                .orElseThrow(() -> new BadRequestException("No hay un período académico vigente"));
        Instant ahora = Instant.now();
        if (ahora.isBefore(periodo.getPlazoDocenteInicio()) || ahora.isAfter(periodo.getPlazoDocenteFin())) {
            throw new BadRequestException("El plazo de registro ya cerró");
        }
        if (request.dia() < 1 || request.dia() > 7) {
            throw new BadRequestException("El día debe estar entre lunes y domingo");
        }
        if (!request.horaFin().isAfter(request.horaInicio())) {
            throw new BadRequestException("La hora de fin debe ser posterior a la hora de inicio");
        }
        if (request.horaInicio().isBefore(horaMinima) || request.horaFin().isAfter(horaMaxima)) {
            throw new BadRequestException("Hora fuera del rango permitido");
        }
        List<DisponibilidadDocente> cruces = disponibilidadRepository.findCruces(
                usuarioId, periodo.getId(), request.dia(), request.horaInicio(), request.horaFin());
        if (!cruces.isEmpty()) {
            DisponibilidadDocente existente = cruces.get(0);
            throw new BadRequestException("El horario se cruza con otro ya registrado ("
                    + nombreDia(existente.getDia()) + " " + existente.getHoraInicio() + " a " + existente.getHoraFin() + ")");
        }
        Usuario docente = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Docente no encontrado"));
        DisponibilidadDocente guardado = disponibilidadRepository.save(DisponibilidadDocente.builder()
                .docente(docente).periodo(periodo).dia(request.dia())
                .horaInicio(request.horaInicio()).horaFin(request.horaFin()).build());
        return toItem(guardado);
    }

    private List<DisponibilidadDocenteDto.Item> listar(Integer usuarioId, Long periodoId) {
        return disponibilidadRepository.findAllByDocenteIdAndPeriodoIdOrderByDiaAscHoraInicioAsc(usuarioId, periodoId)
                .stream().map(this::toItem).toList();
    }

    private DisponibilidadDocenteDto.Item toItem(DisponibilidadDocente horario) {
        return new DisponibilidadDocenteDto.Item(horario.getId(), horario.getDia(),
                horario.getHoraInicio(), horario.getHoraFin());
    }

    private String nombreDia(int dia) {
        return List.of("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo").get(dia - 1);
    }
}
