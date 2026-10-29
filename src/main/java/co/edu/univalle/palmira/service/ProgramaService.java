package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.ProgramaDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.model.entity.Coordinador;
import co.edu.univalle.palmira.model.enums.EstadoPrograma;
import co.edu.univalle.palmira.model.enums.Jornada;
import co.edu.univalle.palmira.model.enums.TipoIngreso;
import co.edu.univalle.palmira.repository.PensumAcademicoRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import co.edu.univalle.palmira.repository.CoordinadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.util.Map;

// HU-040: crear, editar y desactivar programas académicos (docs/contratos/HU-040.md)
@Service
@RequiredArgsConstructor
public class ProgramaService {

    static final String MSG_NO_ENCONTRADO = "Programa no encontrado";
    static final String MSG_CODIGO_REPETIDO = "Ya existe un programa con este código";
    static final String MSG_CODIGO_NO_EDITABLE = "El código del programa no se puede modificar";

    private final ProgramaRepository programaRepository;
    private final PensumAcademicoRepository pensumAcademicoRepository;
    private final CoordinadorRepository coordinadorRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // Criterio 5: el administrador ve todos los programas, activos e inactivos
    @Transactional(readOnly = true)
    public List<ProgramaDto.Response> getAll() {
        // Un solo GROUP BY en vez de un COUNT por cada programa
        Map<String, Long> conteos = new HashMap<>();
        for (Object[] fila : pensumAcademicoRepository.contarAsignaturasPorPrograma()) {
            conteos.put((String) fila[0], (Long) fila[1]);
        }
        return programaRepository.findAllByOrderByNameProgramAsc()
                .stream()
                .map(p -> toResponse(p, conteos.getOrDefault(p.getCodeProgram(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgramaDto.Response getByCode(String codeProgram) {
        return toResponseWithCount(buscar(codeProgram));
    }

    @Transactional
    public ProgramaDto.Response create(ProgramaDto.CreateRequest request) {
        String code = request.codeProgram().trim();
        if (programaRepository.existsById(code)) {
            throw new ConflictException(MSG_CODIGO_REPETIDO, null);
        }

        Jornada jornada = Jornada.valueOf(request.jornada());
        Integer diurna = jornada == Jornada.NOCTURNA ? null : request.numeroSemestre();
        Integer nocturna = jornada == Jornada.DIURNA ? null : request.semestresNocturna();
        validarDuraciones(jornada, diurna, nocturna);

        Programa programa = Programa.builder()
                .codeProgram(code)
                .nameProgram(request.nameProgram().trim())
                .jornada(jornada)
                .numeroSemestre(diurna)
                .semestresNocturna(nocturna)
                .ingreso(TipoIngreso.valueOf(request.ingreso()))
                .estado(request.estado() != null ? EstadoPrograma.valueOf(request.estado()) : EstadoPrograma.ACTIVO)
                .idFacultadPrograma(request.idFacultadPrograma())
                .apertura(normalizeApertura(request.apertura()))
                .numeroResolucion(request.numeroResolucion() == null ? null : request.numeroResolucion().trim())
                .build();

        // Criterio 3: un programa nuevo no tiene malla ni cohortes
        return toResponse(programaRepository.save(programa), 0L);
    }

    // Criterio 4: editar no toca mallas, cohortes ni coordinador (solo columnas de
    // programas)
    @Transactional
    public ProgramaDto.Response update(String codeProgram, ProgramaDto.UpdateRequest request) {
        Programa programa = buscar(codeProgram);

        if (request.codeProgram() != null && !request.codeProgram().trim().equals(programa.getCodeProgram())) {
            throw new BadRequestException(MSG_CODIGO_NO_EDITABLE);
        }

        Jornada jornada = request.jornada() != null ? Jornada.valueOf(request.jornada()) : programa.getJornada();
        Integer diurna = request.numeroSemestre() != null ? request.numeroSemestre() : programa.getNumeroSemestre();
        Integer nocturna = request.semestresNocturna() != null ? request.semestresNocturna()
                : programa.getSemestresNocturna();
        // La duración de la jornada que ya no se oferta queda vacía
        if (jornada == Jornada.DIURNA) {
            nocturna = null;
        } else if (jornada == Jornada.NOCTURNA) {
            diurna = null;
        }
        validarDuraciones(jornada, diurna, nocturna);

        // Se ejecuta dentro de esta misma transacción: si el intercambio falla,
        // también se revierten los cambios de los campos del programa.
        if (request.idCoordinadorNuevo() != null) {
            intercambiarCoordinador(
                    programa.getCodeProgram(),
                    request.idCoordinadorNuevo()
            );
        }

        if (request.nameProgram() != null) {
            programa.setNameProgram(request.nameProgram().trim());
        }
        programa.setJornada(jornada);
        programa.setNumeroSemestre(diurna);
        programa.setSemestresNocturna(nocturna);
        if (request.ingreso() != null) {
            programa.setIngreso(TipoIngreso.valueOf(request.ingreso()));
        }
        if (request.estado() != null) {
            programa.setEstado(EstadoPrograma.valueOf(request.estado()));
        }
        if (request.idFacultadPrograma() != null) {
            programa.setIdFacultadPrograma(request.idFacultadPrograma());
        }
        if (request.apertura() != null) {
            programa.setApertura(normalizeApertura(request.apertura()));
        }
        if (request.numeroResolucion() != null && !request.numeroResolucion().isBlank()) {
            programa.setNumeroResolucion(request.numeroResolucion().trim());
        }

        return toResponseWithCount(programaRepository.save(programa));
    }

    /**
     * Intercambia al coordinador del programa editado con el coordinador elegido.
     * Ambos cambios se confirman o revierten juntos gracias a la transacción de update().
     */
    private void intercambiarCoordinador(String codeProgram, Integer idCoordinadorNuevo) {
        String codigoActual = codeProgram.trim();
        Coordinador elegidoInicial = coordinadorRepository.findByIdWithDetails(idCoordinadorNuevo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Coordinador no encontrado con ID: " + idCoordinadorNuevo));
        String codigoAnteriorInicial = elegidoInicial.getPrograma() != null
                ? elegidoInicial.getPrograma().getCodeProgram()
                : null;

        // Bloquear primero los programas en orden de código (estable) y después
        // los coordinadores por ID para serializar intercambios concurrentes.
        Set<String> codigosBloqueados = new TreeSet<>();
        codigosBloqueados.add(codigoActual);
        if (codigoAnteriorInicial != null) {
            codigosBloqueados.add(codigoAnteriorInicial);
        }
        List<Programa> programasBloqueados = programaRepository
                .findAllByCodeProgramInForUpdate(codigosBloqueados);
        Map<String, Programa> programasPorCodigo = new HashMap<>();
        for (Programa bloqueado : programasBloqueados) {
            programasPorCodigo.put(bloqueado.getCodeProgram(), bloqueado);
        }
        Programa programaActual = programasPorCodigo.get(codigoActual);
        if (programaActual == null) {
            throw new ResourceNotFoundException(MSG_NO_ENCONTRADO);
        }
        Programa programaAnteriorElegido = codigoAnteriorInicial == null
                ? null
                : programasPorCodigo.get(codigoAnteriorInicial);
        if (codigoAnteriorInicial != null && programaAnteriorElegido == null) {
            throw new ConflictException(
                    "El programa asignado al coordinador seleccionado ya no existe; vuelve a cargar la pantalla.", null);
        }

        List<Coordinador> propietariosActualesIniciales = coordinadorRepository
                .findByProgramaCodeProgram(codigoActual);
        List<Coordinador> propietariosAnterioresIniciales = codigoAnteriorInicial == null
                ? List.of()
                : coordinadorRepository.findByProgramaCodeProgram(codigoAnteriorInicial);

        Set<Integer> idsBloqueados = new TreeSet<>();
        idsBloqueados.add(idCoordinadorNuevo);
        propietariosActualesIniciales.forEach(c -> idsBloqueados.add(c.getId()));
        propietariosAnterioresIniciales.forEach(c -> idsBloqueados.add(c.getId()));
        List<Coordinador> coordinadoresBloqueados = coordinadorRepository
                .findAllByIdsForUpdate(idsBloqueados);
        // Las entidades pueden haber sido leídas antes de obtener los bloqueos;
        // refresh asegura que validamos el estado actual y no una copia obsoleta.
        coordinadoresBloqueados.forEach(c -> entityManager.refresh(c, LockModeType.PESSIMISTIC_WRITE));
        Map<Integer, Coordinador> coordinadoresPorId = new HashMap<>();
        coordinadoresBloqueados.forEach(c -> coordinadoresPorId.put(c.getId(), c));

        Coordinador elegido = coordinadoresPorId.get(idCoordinadorNuevo);
        if (elegido == null) {
            throw new ResourceNotFoundException("Coordinador no encontrado con ID: " + idCoordinadorNuevo);
        }
        String codigoAnteriorElegido = elegido.getPrograma() != null
                ? elegido.getPrograma().getCodeProgram()
                : null;
        if (!Objects.equals(codigoAnteriorInicial, codigoAnteriorElegido)) {
            throw new ConflictException(
                    "La asignación del coordinador cambió durante la operación. Vuelve a cargar la pantalla e inténtalo de nuevo.", null);
        }

        List<Coordinador> propietariosActuales = coordinadoresBloqueados.stream()
                .filter(c -> c.getPrograma() != null
                        && codigoActual.equals(c.getPrograma().getCodeProgram()))
                .toList();
        if (propietariosActuales.size() > 1) {
            throw new ConflictException(
                    "El programa que intentas editar ya tiene varios coordinadores asignados. Corrige los datos antes de continuar.", null);
        }

        if (codigoAnteriorElegido != null) {
            List<Coordinador> propietariosProgramaAnterior =
                    coordinadoresBloqueados.stream()
                            .filter(c -> c.getPrograma() != null
                                    && codigoAnteriorElegido.equals(
                                    c.getPrograma().getCodeProgram()))
                            .toList();

            if (propietariosProgramaAnterior.size() > 1) {
                throw new ConflictException(
                        "El programa del coordinador seleccionado tiene asignaciones duplicadas. Corrige los datos antes de continuar.",
                        null);
            }
        }

        Coordinador actual = propietariosActuales.isEmpty()
                ? null
                : propietariosActuales.get(0);

// Si ya es el coordinador asignado, no hay cambios que realizar.
        if (actual != null && actual.getId().equals(elegido.getId())) {
            return;
        }

// Solo se pueden asignar coordinadores activos.
        if (!Boolean.TRUE.equals(elegido.getActivo())) {
            throw new BadRequestException(
                    "No se puede asignar un coordinador inactivo.");
        }

// Liberar el programa del coordinador anterior.
// El guardado y la asignación siguiente pertenecen a la misma
// transacción de update().
        if (actual != null) {
            actual.setPrograma(null);
            coordinadorRepository.saveAndFlush(actual);
        }

// Asignar el programa editado al coordinador seleccionado.
// Si dirigía otro programa, ese programa quedará sin coordinador.
        elegido.setPrograma(programaActual);
        coordinadorRepository.saveAndFlush(elegido);
    }

    // RN-13: no se borra; se desactiva y se conservan malla, cohortes, coordinador
    // y docentes
    @Transactional
    public void desactivar(String codeProgram) {
        Programa programa = buscar(codeProgram);
        programa.setEstado(EstadoPrograma.INACTIVO);
        programaRepository.save(programa);
    }

    private Programa buscar(String codeProgram) {
        return programaRepository.findById(codeProgram.trim())
                .orElseThrow(() -> new ResourceNotFoundException(MSG_NO_ENCONTRADO));
    }

    private void validarDuraciones(Jornada jornada, Integer diurna, Integer nocturna) {
        if (jornada != Jornada.NOCTURNA && !enRango(diurna)) {
            throw new BadRequestException(ProgramaDto.MSG_DURACION_DIURNA);
        }
        if (jornada != Jornada.DIURNA && !enRango(nocturna)) {
            throw new BadRequestException(ProgramaDto.MSG_DURACION_NOCTURNA);
        }
    }

    private boolean enRango(Integer semestres) {
        return semestres != null && semestres >= 1 && semestres <= 20;
    }

    private ProgramaDto.Response toResponseWithCount(Programa p) {
        return toResponse(p, pensumAcademicoRepository.countByIdCodeProgram(p.getCodeProgram()));
    }

    private ProgramaDto.Response toResponse(Programa p, long totalAsignaturas) {
        return new ProgramaDto.Response(
                p.getCodeProgram(),
                p.getNameProgram(),
                p.getJornada(),
                p.getNumeroSemestre(),
                p.getSemestresNocturna(),
                p.getIngreso(),
                p.getEstado(),
                p.getIdFacultadPrograma(),
                totalAsignaturas,
                p.getApertura(),
                p.getNumeroResolucion());
    }

    private String normalizeApertura(String apertura) {
        return apertura == null || apertura.isBlank() ? null : apertura.trim().toUpperCase();
    }
}