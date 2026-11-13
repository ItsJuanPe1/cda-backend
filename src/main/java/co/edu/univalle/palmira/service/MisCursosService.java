package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.config.CargaProperties;
import co.edu.univalle.palmira.dto.MisCursosDto;
import co.edu.univalle.palmira.model.entity.CursoProgramacion;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.repository.CursoProgramacionRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Cursos que un docente tiene asignados en el período vigente. El docente se identifica
 * siempre por el correo de su sesión: nunca se recibe un id, así que nadie puede consultar
 * los cursos de otro docente.
 */
@Service
@RequiredArgsConstructor
public class MisCursosService {

    private final DocenteRepository docenteRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final CursoProgramacionRepository cursoRepository;
    private final CargaProperties cargaProperties;

    @Transactional(readOnly = true)
    public MisCursosDto.Respuesta consultar(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new AccessDeniedException(AccesoProgramaService.MSG_SIN_PERMISO);
        }
        Docente docente = docenteRepository.findByCorreoInstitucionalAndEstadoTrue(correo.trim())
                .orElseThrow(() -> new AccessDeniedException(AccesoProgramaService.MSG_SIN_PERMISO));

        int limite = cargaProperties.getMaxHorasDocente();
        Optional<PeriodoAcademico> vigente = periodoRepository.findFirstByVigenteTrue()
                .filter(p -> p.isVigente()
                        && (p.getEstado() == null || "VIGENTE".equalsIgnoreCase(p.getEstado())));
        if (vigente.isEmpty()) {
            return new MisCursosDto.Respuesta(null, 0, limite, limite, List.of());
        }
        PeriodoAcademico periodo = vigente.get();

        List<MisCursosDto.Curso> cursos = cursoRepository
                .findByDocenteYPeriodo(docente.getId(), periodo.getId()).stream()
                .sorted(Comparator
                        .comparing((CursoProgramacion c) -> c.getAsignatura().getCodeCourse())
                        .thenComparing(CursoProgramacion::getGrupo,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .map(MisCursosService::toCurso)
                .toList();

        int total = cursos.stream().mapToInt(MisCursosDto.Curso::horas).sum();
        return new MisCursosDto.Respuesta(
                new MisCursosDto.Periodo(periodo.getId(), periodo.getNombre()),
                total, limite, Math.max(0, limite - total), cursos);
    }

    private static MisCursosDto.Curso toCurso(CursoProgramacion c) {
        Integer horas = c.getAsignatura().getHorasSemanales();
        String codigo = c.getAsignatura().getCodeCourse();
        return new MisCursosDto.Curso(
                c.getId(),
                c.getGrupo() != null ? codigo + "-" + c.getGrupo() : codigo,
                c.getNombreAsignatura(),
                c.getPrograma().getNameProgram(),
                c.getJornada() != null ? c.getJornada().name() : null,
                horas != null ? horas : 0,
                horas == null);
    }
}