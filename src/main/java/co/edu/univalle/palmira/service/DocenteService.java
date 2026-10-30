package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.DocenteDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.DocenteFoto;
import co.edu.univalle.palmira.model.entity.Facultad;
import co.edu.univalle.palmira.model.entity.Programa;
import co.edu.univalle.palmira.repository.DocenteFotoRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.FacultadRepository;
import co.edu.univalle.palmira.repository.ProgramaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DocenteService {

    // HU-015: límites y mensajes de "Mi perfil"
    public static final long FOTO_TAMANO_MAXIMO = 5L * 1024 * 1024;
    public static final String MSG_FOTO_INVALIDA = "Formato o tamaño no válido";
    public static final String MSG_SIN_FOTO = "No tiene foto registrada";
    public static final String MSG_SIN_CAMBIOS = "No hay cambios para guardar";
    public static final String MSG_PERFIL_NO_ENCONTRADO = "No se encontró su perfil de docente";

    private static final byte[] FIRMA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final DocenteRepository docenteRepository;
    private final ProgramaRepository programaRepository;
    private final FacultadRepository facultadRepository;
    private final DocenteFotoRepository docenteFotoRepository;

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

        // Código y correo duplicados: 409 sin id existente
        if (docenteRepository.existsByCodigo(req.codigo())) {
            throw new ConflictException(
                    "Ya existe un docente con el código " + req.codigo(), null);
        }
        if (docenteRepository.existsByCorreoInstitucional(req.correoInstitucional())) {
            throw new ConflictException(
                    "Ya existe un docente con el correo " + req.correoInstitucional(), null);
        }

        Facultad facultad = null;
        Programa programa = null;

        if (req.idFacultad() != null) {
            facultad = buscarFacultad(req.idFacultad());
        } else if (req.codeProgram() != null && !req.codeProgram().isBlank()) {
            programa = buscarPrograma(req.codeProgram());
            if (programa.getIdFacultadPrograma() != null) {
                facultad = facultadRepository.findById(programa.getIdFacultadPrograma()).orElse(null);
            }
        } else {
            throw new BadRequestException("La facultad de adscripción es obligatoria");
        }

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
                .facultad(facultad)
                .programa(programa)
                .fotoUrl(req.fotoUrl())
                .evaluacionPeriodo(req.evaluacionPeriodo())
                .evaluacionValor(req.evaluacionValor())
                .build();

        return toResponse(docenteRepository.save(docente), false);
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

        if (req.idFacultad() != null) {
            docente.setFacultad(buscarFacultad(req.idFacultad()));
        } else if (req.codeProgram() != null && !req.codeProgram().isBlank()) {
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
        Set<Long> conFoto = docenteFotoRepository.findDocenteIdsConFotoActiva();
        return docenteRepository.findAllWithPrograma()
                .stream()
                .map(d -> toResponse(d, conFoto.contains(d.getId())))
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
    // HU-022: Mi perfil (el correo sale siempre del JWT, nunca de la petición)
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public DocenteDto.Response obtenerPorCorreo(String correo) {
        return toResponse(buscarPerfilPropio(correo));
    }

    // ---------------------------------------------------------------
    // HU-015: Editar mis datos de contacto (correo personal y teléfono).
    // Nombres, apellidos, correo institucional y demás datos no se editan aquí.
    // ---------------------------------------------------------------
    @Transactional
    public DocenteDto.Response actualizarContacto(String correo, DocenteDto.ContactoRequest req) {
        Docente docente = buscarPerfilPropio(correo);

        String correoPersonal = normalizarOpcional(req.correoPersonal());
        if (correoPersonal != null) {
            correoPersonal = correoPersonal.toLowerCase();
        }
        String telefono = normalizarOpcional(req.telefono());

        if (Objects.equals(correoPersonal, docente.getCorreoPersonal())
                && Objects.equals(telefono, docente.getTelefono())) {
            throw new BadRequestException(MSG_SIN_CAMBIOS);
        }

        docente.setCorreoPersonal(correoPersonal);
        docente.setTelefono(telefono);
        return toResponse(docenteRepository.save(docente));
    }

    // ---------------------------------------------------------------
    // HU-015: Subir o reemplazar mi foto. Solo se guarda una foto por docente:
    // la nueva reemplaza el contenido de la anterior y queda activa.
    // ---------------------------------------------------------------
    @Transactional
    public DocenteDto.Response actualizarFoto(String correo, MultipartFile archivo) {
        Docente docente = buscarPerfilPropio(correo);

        if (archivo == null || archivo.isEmpty() || archivo.getSize() > FOTO_TAMANO_MAXIMO) {
            throw new BadRequestException(MSG_FOTO_INVALIDA);
        }
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen", e);
        }
        // El tipo se decide por el contenido del archivo, no por la extensión ni el Content-Type
        String tipoMime = detectarTipoImagen(contenido);
        if (tipoMime == null) {
            throw new BadRequestException(MSG_FOTO_INVALIDA);
        }

        DocenteFoto foto = docenteFotoRepository.findByDocenteId(docente.getId())
                .orElseGet(() -> DocenteFoto.builder().docente(docente).build());
        foto.setContenido(contenido);
        foto.setTipoMime(tipoMime);
        foto.setTamanoBytes(contenido.length);
        foto.setActiva(true);
        docenteFotoRepository.save(foto);

        return toResponse(docente, true);
    }

    // ---------------------------------------------------------------
    // HU-015: Obtener mi foto activa
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public DocenteDto.Foto obtenerFoto(String correo) {
        Docente docente = buscarPerfilPropio(correo);
        DocenteFoto foto = docenteFotoRepository.findByDocenteIdAndActivaTrue(docente.getId())
                .orElseThrow(() -> new ResourceNotFoundException(MSG_SIN_FOTO));
        return new DocenteDto.Foto(foto.getTipoMime(), foto.getContenido());
    }

    // ---------------------------------------------------------------
    // HU-015: Eliminar mi foto. Se desactiva (RN-13); solo el administrador
    // la borra directamente en la base de datos.
    // ---------------------------------------------------------------
    @Transactional
    public void eliminarFoto(String correo) {
        Docente docente = buscarPerfilPropio(correo);
        DocenteFoto foto = docenteFotoRepository.findByDocenteIdAndActivaTrue(docente.getId())
                .orElseThrow(() -> new ResourceNotFoundException(MSG_SIN_FOTO));
        foto.setActiva(false);
        docenteFotoRepository.save(foto);
    }

    // ---------------------------------------------------------------
    // Listado de docentes (ordenado por apellidos y nombres)
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<DocenteDto.Response> listar() {
        Set<Long> conFoto = docenteFotoRepository.findDocenteIdsConFotoActiva();
        return docenteRepository.findAllByOrderByApellidosAscNombresAsc()
                .stream()
                .map(d -> toResponse(d, conFoto.contains(d.getId())))
                .toList();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private Docente buscarPerfilPropio(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new ResourceNotFoundException(MSG_PERFIL_NO_ENCONTRADO);
        }
        return docenteRepository
                .findByCorreoInstitucionalAndEstadoTrue(correo.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException(MSG_PERFIL_NO_ENCONTRADO));
    }

    /** null o solo espacios se guardan como null; el resto, sin espacios en los extremos. */
    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    /** Devuelve image/jpeg o image/png según la firma del archivo, o null si no es ninguno. */
    static String detectarTipoImagen(byte[] contenido) {
        if (empiezaCon(contenido, FIRMA_JPEG)) {
            return "image/jpeg";
        }
        if (empiezaCon(contenido, FIRMA_PNG)) {
            return "image/png";
        }
        return null;
    }

    private static boolean empiezaCon(byte[] contenido, byte[] firma) {
        return contenido != null && contenido.length >= firma.length
                && Arrays.equals(contenido, 0, firma.length, firma, 0, firma.length);
    }

    private Facultad buscarFacultad(Integer idFacultad) {
        return facultadRepository.findById(idFacultad)
                .orElseThrow(() -> new BadRequestException(
                        "La facultad de adscripción no existe: " + idFacultad));
    }

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
        return toResponse(d, docenteFotoRepository.existsByDocenteIdAndActivaTrue(d.getId()));
    }

    private DocenteDto.Response toResponse(Docente d, boolean tieneFoto) {
        Integer idFacultad = d.getFacultad() != null
                ? d.getFacultad().getId()
                : (d.getPrograma() != null ? d.getPrograma().getIdFacultadPrograma() : null);
        String nombreFacultad = d.getFacultad() != null ? d.getFacultad().getNombre() : null;

        return new DocenteDto.Response(
                d.getId(),
                d.getCodigo(),
                d.getNombres(),
                d.getApellidos(),
                d.getTipoDocumento(),
                d.getNumeroDocumento(),
                d.getCorreoInstitucional(),
                d.getCorreoPersonal(),
                d.getTelefono(),
                d.getTipoDocente(),
                d.getCategoria(),
                idFacultad,
                nombreFacultad,
                d.getPrograma() != null ? d.getPrograma().getCodeProgram() : null,
                d.getPrograma() != null ? d.getPrograma().getNameProgram() : null,
                d.getFotoUrl(),
                tieneFoto,
                d.getEvaluacionPeriodo(),
                d.getEvaluacionValor(),
                d.getEstado(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }
}
