package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.dto.AccesoExtraordinarioDto;
import co.edu.univalle.palmira.exception.BadRequestException;
import co.edu.univalle.palmira.exception.ConflictException;
import co.edu.univalle.palmira.exception.ResourceNotFoundException;
import co.edu.univalle.palmira.model.entity.AccesoExtraordinarioDocente;
import co.edu.univalle.palmira.model.entity.Docente;
import co.edu.univalle.palmira.model.entity.PeriodoAcademico;
import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.repository.AccesoExtraordinarioRepository;
import co.edu.univalle.palmira.repository.DocenteRepository;
import co.edu.univalle.palmira.repository.PeriodoAcademicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Acceso extraordinario: plazo que el administrador abre a un docente para editar su
 * disponibilidad aunque la ventana general esté cerrada. El vencimiento no usa tareas
 * programadas: "vigente" se decide comparando con la hora actual cada vez que se consulta.
 */
@Service
@RequiredArgsConstructor
public class AccesoExtraordinarioService {

    public static final String ENTIDAD = "ACCESO_EXTRAORDINARIO";
    public static final int MAX_DIAS = 30;
    public static final int MAX_MOTIVO = 500;

    public static final String MSG_YA_VIGENTE =
            "El docente ya tiene un acceso extraordinario vigente; revóquelo antes de habilitar otro";
    public static final String MSG_SIN_VIGENTE = "El docente no tiene un acceso extraordinario vigente";
    public static final String MSG_FIN_OBLIGATORIO = "La fecha y hora de fin son obligatorias";
    public static final String MSG_FIN_PASADO = "La fecha y hora de fin deben ser posteriores al momento actual";
    public static final String MSG_FIN_LEJANO = "El acceso extraordinario no puede durar más de 30 días";
    public static final String MSG_MOTIVO = "El motivo es obligatorio";
    public static final String MSG_MOTIVO_LARGO = "El motivo no puede exceder 500 caracteres";
    public static final String MSG_DOCENTE = "Docente no encontrado";

    private final AccesoExtraordinarioRepository accesoRepository;
    private final DocenteRepository docenteRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final BitacoraService bitacoraService;

    @Transactional
    public AccesoExtraordinarioDto.Respuesta otorgar(Long docenteId, String correoAdmin,
                                                     AccesoExtraordinarioDto.Request request) {
        String admin = exigirCorreo(correoAdmin);
        if (request == null || request.fechaFin() == null) {
            throw new BadRequestException(MSG_FIN_OBLIGATORIO);
        }
        String motivo = request.motivo() == null ? "" : request.motivo().trim();
        if (motivo.isEmpty()) {
            throw new BadRequestException(MSG_MOTIVO);
        }
        if (motivo.length() > MAX_MOTIVO) {
            throw new BadRequestException(MSG_MOTIVO_LARGO);
        }
        Instant ahora = Instant.now();
        Instant fin = request.fechaFin();
        if (!fin.isAfter(ahora)) {
            throw new BadRequestException(MSG_FIN_PASADO);
        }
        if (fin.isAfter(ahora.plus(MAX_DIAS, ChronoUnit.DAYS))) {
            throw new BadRequestException(MSG_FIN_LEJANO);
        }

        // Se bloquea la fila del docente para que dos peticiones simultáneas no creen dos accesos vigentes.
        Docente docente = docenteBloqueado(docenteId);
        if (Boolean.FALSE.equals(docente.getEstado())) {
            throw new ResourceNotFoundException(MSG_DOCENTE);
        }
        if (vigente(docenteId, ahora).isPresent()) {
            throw new ConflictException(MSG_YA_VIGENTE);
        }

        AccesoExtraordinarioDocente guardado = accesoRepository.save(AccesoExtraordinarioDocente.builder()
                .docente(docente)
                .fechaInicio(ahora)
                .fechaFin(fin)
                .motivo(motivo)
                .otorgadoPor(admin)
                .build());

        bitacoraService.registrar(AccionBitacora.CREAR, ENTIDAD, String.valueOf(guardado.getId()),
                periodoVigenteId(), null, datos(guardado, docente));
        return toRespuesta(guardado, docenteId);
    }

    @Transactional(readOnly = true)
    public Optional<AccesoExtraordinarioDto.Respuesta> consultar(Long docenteId) {
        if (!docenteRepository.existsById(docenteId)) {
            throw new ResourceNotFoundException(MSG_DOCENTE);
        }
        return vigente(docenteId, Instant.now()).map(a -> toRespuesta(a, docenteId));
    }

    /** Acceso vigente del docente en el instante dado, si lo hay. */
    @Transactional(readOnly = true)
    public Optional<AccesoExtraordinarioDocente> vigente(Long docenteId, Instant ahora) {
        return accesoRepository.findByDocenteIdAndRevocadoEnIsNullOrderByFechaFinDesc(docenteId).stream()
                .filter(a -> a.estaVigenteEn(ahora))
                .findFirst();
    }

    @Transactional
    public void revocar(Long docenteId, String correoAdmin) {
        String admin = exigirCorreo(correoAdmin);
        Docente docente = docenteBloqueado(docenteId);
        Instant ahora = Instant.now();
        AccesoExtraordinarioDocente acceso = vigente(docenteId, ahora)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_SIN_VIGENTE));

        Map<String, Object> anterior = datos(acceso, docente);
        acceso.setRevocadoEn(ahora);
        acceso.setRevocadoPor(admin);
        accesoRepository.save(acceso);

        bitacoraService.registrar(AccionBitacora.DESACTIVAR, ENTIDAD, String.valueOf(acceso.getId()),
                periodoVigenteId(), anterior, datos(acceso, docente));
    }

    private Docente docenteBloqueado(Long docenteId) {
        return docenteRepository.findByIdForUpdate(docenteId)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_DOCENTE));
    }

    private static String exigirCorreo(String correoAdmin) {
        if (correoAdmin == null || correoAdmin.isBlank()) {
            throw new AccessDeniedException(AccesoProgramaService.MSG_SIN_PERMISO);
        }
        return correoAdmin.trim();
    }

    private Long periodoVigenteId() {
        return periodoRepository.findFirstByVigenteTrue().map(PeriodoAcademico::getId).orElse(null);
    }

    /** Datos de negocio para la bitácora: docente, motivo y fechas (fechas como texto ISO). */
    private static Map<String, Object> datos(AccesoExtraordinarioDocente a, Docente docente) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("docente_id", docente.getId());
        datos.put("docente", ((docente.getNombres() != null ? docente.getNombres() : "") + " "
                + (docente.getApellidos() != null ? docente.getApellidos() : "")).trim());
        datos.put("fecha_inicio", a.getFechaInicio().toString());
        datos.put("fecha_fin", a.getFechaFin().toString());
        datos.put("motivo", a.getMotivo());
        datos.put("otorgado_por", a.getOtorgadoPor());
        if (a.getRevocadoEn() != null) {
            datos.put("revocado_en", a.getRevocadoEn().toString());
            datos.put("revocado_por", a.getRevocadoPor());
        }
        return datos;
    }

    private static AccesoExtraordinarioDto.Respuesta toRespuesta(AccesoExtraordinarioDocente a, Long docenteId) {
        return new AccesoExtraordinarioDto.Respuesta(a.getId(), docenteId, a.getFechaInicio(),
                a.getFechaFin(), a.getMotivo(), a.getOtorgadoPor());
    }
}