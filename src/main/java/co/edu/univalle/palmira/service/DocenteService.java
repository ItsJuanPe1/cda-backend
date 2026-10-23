package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.DocenteDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocenteService {

    private final DocenteRepository docenteRepository;
    private final ProgramaRepository programaRepository;

    // ---------------------------------------------------------------
    // HU-026: Crear perfil de docente
    // ---------------------------------------------------------------
    @Transactional
    public DocenteDto.Response crear(DocenteDto.CreateRequest req) {
        validarEvaluacion(req.evaluacionPeriodo(), req.evaluacionValor());

        // Documento duplicado: el front ofrece actualizar (necesita el id existente)
        docenteRepository.findByNumeroDocumento(req.numeroDocumento())
                .ifPresent(existente -> {
                    throw new ConflictException(
                            "Ya existe un docente con el documento " + req.numeroDocumento(),
                            existente.getId());
                });

        // Código y correo duplicados: 409 sin id existente (no es el flujo "¿desea
        // actualizar?")
        if (docenteRepository.existsByCodigo(req.codigo())) {
            throw new ConflictException(
                    "Ya existe un docente con el código " + req.codigo(), null);
        }
        if (docenteRepository.existsByCorreoInstitucional(req.correoInstitucional())) {
            throw new ConflictException(
                    "Ya existe un docente con el correo " + req.correoInstitucional(), null);
        }

        Programa programa = buscarPrograma(req.codeProgram());

        Docente docente = Docente.builder()
                .codigo(req.codigo().trim())
                .nombres(req.nombres().trim())
                .apellidos(req.apellidos().trim())
                .tipoDocumento(req.tipoDocumento())
                .numeroDocumento(req.numeroDocumento())
                .correoInstitucional(req.correoInstitucional().trim().toLowerCase())
                .telefono(req.telefono())
                .tipoDocente(req.tipoDocente())
                .categoria(req.categoria())
                .programa(programa)
                .fotoUrl(req.fotoUrl())
                .evaluacionPeriodo(req.evaluacionPeriodo())
                .evaluacionValor(req.evaluacionValor())
                .build();

        return toResponse(docenteRepository.save(docente));
    }

    // ---------------------------------------------------------------
    // Actualizar (flujo "ya existe, ¿desea actualizarla?")
    // Los campos null no se modifican.
    // ---------------------------------------------------------------
    @Transactional
    public DocenteDto.Response actualizar(Long id, DocenteDto.UpdateRequest req) {
        Docente docente = docenteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Docente no encontrado con id " + id));

        if (req.codigo() != null && !req.codigo().equals(docente.getCodigo())) {
            if (docenteRepository.existsByCodigo(req.codigo())) {
                throw new ConflictException("Ya existe un docente con el código " + req.codigo(), null);
            }
            docente.setCodigo(req.codigo().trim());
        }

        if (req.numeroDocumento() != null && !req.numeroDocumento().equals(docente.getNumeroDocumento())) {
            docenteRepository.findByNumeroDocumento(req.numeroDocumento())
                    .ifPresent(otro -> {
                        throw new ConflictException(
                                "Ya existe un docente con el documento " + req.numeroDocumento(),
                                otro.getId());
                    });
            docente.setNumeroDocumento(req.numeroDocumento());
        }

        if (req.correoInstitucional() != null
                && !req.correoInstitucional().equalsIgnoreCase(docente.getCorreoInstitucional())) {
            if (docenteRepository.existsByCorreoInstitucional(req.correoInstitucional())) {
                throw new ConflictException(
                        "Ya existe un docente con el correo " + req.correoInstitucional(), null);
            }
            docente.setCorreoInstitucional(req.correoInstitucional().trim().toLowerCase());
        }

        if (req.nombres() != null)
            docente.setNombres(req.nombres().trim());
        if (req.apellidos() != null)
            docente.setApellidos(req.apellidos().trim());
        if (req.tipoDocumento() != null)
            docente.setTipoDocumento(req.tipoDocumento());
        if (req.telefono() != null)
            docente.setTelefono(req.telefono());
        if (req.tipoDocente() != null)
            docente.setTipoDocente(req.tipoDocente());
        if (req.categoria() != null)
            docente.setCategoria(req.categoria());
        if (req.fotoUrl() != null)
            docente.setFotoUrl(req.fotoUrl());

        if (req.codeProgram() != null) {
            docente.setPrograma(buscarPrograma(req.codeProgram()));
        }

        // Evaluación: dos campos simples que se sobreescriben (sin historial)
        if (req.evaluacionPeriodo() != null)
            docente.setEvaluacionPeriodo(req.evaluacionPeriodo());
        if (req.evaluacionValor() != null)
            docente.setEvaluacionValor(req.evaluacionValor());
        validarEvaluacion(docente.getEvaluacionPeriodo(), docente.getEvaluacionValor());

        return toResponse(docenteRepository.save(docente));
    }

    // ---------------------------------------------------------------
    // Listar todos los docentes para Plantilla
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<DocenteDto.Response> listarTodos() {
        return docenteRepository.findAllWithPrograma()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ---------------------------------------------------------------
    // Activar / Inactivar docente
    // ---------------------------------------------------------------
    @Transactional
    public DocenteDto.Response cambiarEstado(Long id, Boolean estado) {
        Docente docente = docenteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Docente no encontrado con id " + id));
        docente.setEstado(estado != null ? estado : true);
        return toResponse(docenteRepository.save(docente));
    }

    // ---------------------------------------------------------------
    // Mi perfil
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public DocenteDto.Response obtenerPorCorreo(String correo) {
        Docente docente = docenteRepository
                .findByCorreoInstitucionalAndEstadoTrue(correo.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró un perfil de docente activo para el correo " + correo));
        return toResponse(docente);
    }

    // ---------------------------------------------------------------
    // Listado de docentes (ordenado por apellidos y nombres)
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<DocenteDto.Response> listar() {
        return docenteRepository.findAllByOrderByApellidosAscNombresAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private Programa buscarPrograma(String codeProgram) {
        return programaRepository.findById(codeProgram)
                .orElseThrow(() -> new BadRequestException(
                        "El programa de adscripción no existe: " + codeProgram));
    }

    /** Si hay valor de evaluación, el periodo es obligatorio. */
    private void validarEvaluacion(String periodo, BigDecimal valor) {
        if (valor != null && (periodo == null || periodo.isBlank())) {
            throw new BadRequestException(
                    "El periodo de la evaluación es obligatorio cuando se registra un valor");
        }
    }

    private DocenteDto.Response toResponse(Docente d) {
        return new DocenteDto.Response(
                d.getId(),
                d.getCodigo(),
                d.getNombres(),
                d.getApellidos(),
                d.getTipoDocumento(),
                d.getNumeroDocumento(),
                d.getCorreoInstitucional(),
                d.getTelefono(),
                d.getTipoDocente(),
                d.getCategoria(),
                d.getPrograma() != null ? d.getPrograma().getCodeProgram() : null,
                d.getPrograma() != null ? d.getPrograma().getNameProgram() : null,
                d.getFotoUrl(),
                d.getEvaluacionPeriodo(),
                d.getEvaluacionValor(),
                d.getEstado(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }
}