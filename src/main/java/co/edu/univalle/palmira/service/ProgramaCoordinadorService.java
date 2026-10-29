package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.ProgramaCoordinadorDto;
import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// HU-037: ver programas académicos y sus coordinadores (docs/contratos/HU-037.md)
@Service
@RequiredArgsConstructor
public class ProgramaCoordinadorService {

    private final ProgramaRepository programaRepository;
    private final CoordinadorRepository coordinadorRepository;

    /**
     * Programas de pregrado activos con su coordinador activo, o «Sin coordinador».
     * Son unos 14 programas: la búsqueda y el filtro se aplican en memoria, sin paginación.
     */
    @Transactional(readOnly = true)
    public List<ProgramaCoordinadorDto.Response> listar(String q, boolean sinCoordinador) {
        // Un coordinador desactivado no cuenta: el programa queda «Sin coordinador»
        Map<String, Coordinador> porPrograma = new HashMap<>();
        for (Coordinador c : coordinadorRepository.findActivosConPrograma()) {
            porPrograma.putIfAbsent(c.getPrograma().getCodeProgram(), c);
        }

        String busqueda = normalizar(q);

        return programaRepository.findByEstadoOrderByNameProgramAsc(EstadoPrograma.ACTIVO)
                .stream()
                .map(p -> toResponse(p, porPrograma.get(p.getCodeProgram())))
                .filter(r -> !sinCoordinador || !r.tieneCoordinador())
                .filter(r -> busqueda.isEmpty() || coincide(r, busqueda))
                .toList();
    }

    private static boolean coincide(ProgramaCoordinadorDto.Response r, String busqueda) {
        return contiene(r.codeProgram(), busqueda)
                || contiene(r.nameProgram(), busqueda)
                || (r.coordinador() != null && contiene(r.coordinador().nombreCompleto(), busqueda));
    }

    private static boolean contiene(String valor, String busqueda) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(busqueda);
    }

    private static String normalizar(String q) {
        return q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    }

    private static ProgramaCoordinadorDto.Response toResponse(Programa p, Coordinador c) {
        ProgramaCoordinadorDto.CoordinadorResumen coordinador = c == null ? null
                : new ProgramaCoordinadorDto.CoordinadorResumen(
                        c.getId(), c.getNombreCompleto(), c.getCorreoInstitucional());
        return new ProgramaCoordinadorDto.Response(
                p.getCodeProgram(), p.getNameProgram(), p.getJornada(), c != null, coordinador);
    }
}
