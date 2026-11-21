package co.edu.univalle.palmira.service.notificacion;

import co.edu.univalle.palmira.config.NotificacionesProperties;
import co.edu.univalle.palmira.model.entity.Notificacion;
import co.edu.univalle.palmira.model.entity.NotificacionEnvio;
import co.edu.univalle.palmira.model.enums.EstadoEnvioNotificacion;
import co.edu.univalle.palmira.model.enums.EstadoNotificacion;
import co.edu.univalle.palmira.model.enums.TipoDestinatario;
import co.edu.univalle.palmira.model.enums.TipoEventoNotificacion;
import co.edu.univalle.palmira.repository.NotificacionEnvioRepository;
import co.edu.univalle.palmira.repository.NotificacionRepository;
import co.edu.univalle.palmira.service.correo.CorreoException;
import co.edu.univalle.palmira.service.correo.CorreoSaliente;
import co.edu.univalle.palmira.service.correo.CorreoSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * HU-038: procesa la cola de correos, un elemento por transacción. Así el fallo de un envío no
 * afecta a los demás ni a la operación que lo programó (criterio 8).
 */
@Service
@Slf4j
public class NotificacionProcesador {

    public static final String MOTIVO_EVENTO_NO_VIGENTE = "EVENTO_NO_VIGENTE";
    static final String ERROR_INTERNO = "Error interno al preparar el correo";
    static final String ERROR_SIN_MANEJADOR = "Tipo de evento sin manejador";
    private static final int MAX_ID_PROVEEDOR = 100;

    private final NotificacionRepository notificacionRepository;
    private final NotificacionEnvioRepository envioRepository;
    private final List<ManejadorNotificacion> manejadores;
    private final CorreoSender correoSender;
    private final NotificacionesProperties propiedades;
    private final Clock clock;

    public NotificacionProcesador(NotificacionRepository notificacionRepository,
            NotificacionEnvioRepository envioRepository, List<ManejadorNotificacion> manejadores,
            CorreoSender correoSender, NotificacionesProperties propiedades, Clock clock) {
        this.notificacionRepository = notificacionRepository;
        this.envioRepository = envioRepository;
        this.manejadores = List.copyOf(manejadores);
        this.correoSender = correoSender;
        this.propiedades = propiedades;
        this.clock = clock;
    }

    /**
     * Toma el siguiente evento vencido: resuelve en este momento los destinatarios y crea un envío
     * por cada uno más la copia. Si el período ya no está vigente, cancela el evento (criterio 6).
     *
     * @return false si no había eventos vencidos
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean procesarSiguienteEvento() {
        Instant ahora = clock.instant();
        Optional<Notificacion> siguiente = notificacionRepository.tomarSiguienteVencida(ahora);
        if (siguiente.isEmpty()) {
            return false;
        }
        Notificacion notificacion = siguiente.get();
        Optional<ManejadorNotificacion> manejador = manejador(notificacion.getTipoEvento());
        if (manejador.isEmpty() || !manejador.get().eventoVigente(notificacion)) {
            notificacion.setEstado(EstadoNotificacion.CANCELADO);
            notificacion.setMotivoCancelacion(MOTIVO_EVENTO_NO_VIGENTE);
            notificacion.setCanceladaAt(ahora);
            notificacionRepository.save(notificacion);
            return true;
        }

        ManejadorNotificacion m = manejador.get();
        List<NotificacionEnvio> envios = new ArrayList<>();
        for (Long destinatarioId : new LinkedHashSet<>(m.resolverDestinatarios(notificacion))) {
            envios.add(nuevoEnvio(notificacion, m.tipoDestinatario(), destinatarioId, ahora));
        }
        envios.add(nuevoEnvio(notificacion, TipoDestinatario.COPIA, null, ahora));
        envioRepository.saveAll(envios);

        notificacion.setEstado(EstadoNotificacion.PROCESADO);
        notificacion.setProcesadaAt(ahora);
        notificacionRepository.save(notificacion);
        log.info("Notificación {} ({}) procesada: {} destinatarios y una copia",
                notificacion.getId(), notificacion.getTipoEvento(), envios.size() - 1);
        return true;
    }

    /**
     * Toma el siguiente envío pendiente y lo intenta. Destinatario, correo y estado del perfil se
     * resuelven ahora (criterio 5, HU-015 criterio 9, HU-030).
     *
     * @return false si no había envíos pendientes
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean enviarSiguiente() {
        Instant ahora = clock.instant();
        Optional<NotificacionEnvio> siguiente = envioRepository.tomarSiguientePendiente(ahora);
        if (siguiente.isEmpty()) {
            return false;
        }
        NotificacionEnvio envio = siguiente.get();
        try {
            intentar(envio, ahora);
        } catch (RuntimeException e) {
            // Nunca se escribe el mensaje de la excepción: puede traer datos personales.
            log.error("Error interno al procesar el envío {}: {}", envio.getId(), e.getClass().getSimpleName());
            registrarFallo(envio, ERROR_INTERNO, true, ahora);
        }
        envioRepository.save(envio);
        return true;
    }

    private void intentar(NotificacionEnvio envio, Instant ahora) {
        Notificacion notificacion = notificacionRepository.findById(envio.getNotificacionId()).orElse(null);
        if (notificacion == null || notificacion.getEstado() == EstadoNotificacion.CANCELADO) {
            envio.setEstado(EstadoEnvioNotificacion.CANCELADO);
            return;
        }
        Optional<ManejadorNotificacion> manejador = manejador(notificacion.getTipoEvento());
        if (manejador.isEmpty()) {
            registrarFallo(envio, ERROR_SIN_MANEJADOR, false, ahora);
            return;
        }
        ManejadorNotificacion m = manejador.get();
        if (!m.eventoVigente(notificacion)) {
            envio.setEstado(EstadoEnvioNotificacion.CANCELADO);
            return;
        }

        String para;
        CorreoContenido contenido;
        if (envio.getTipoDestinatario() == TipoDestinatario.COPIA) {
            para = propiedades.getCopia();
            List<Long> ids = envioRepository
                    .findByNotificacionIdAndTipoDestinatarioNotOrderByIdAsc(notificacion.getId(), TipoDestinatario.COPIA)
                    .stream().map(NotificacionEnvio::getDestinatarioId).toList();
            long omitidos = envioRepository.countByNotificacionIdAndEstado(notificacion.getId(),
                    EstadoEnvioNotificacion.OMITIDO);
            contenido = m.construirCopia(notificacion, m.describirDestinatarios(ids), omitidos);
        } else {
            Optional<DestinatarioCorreo> destinatario = m.resolverDestinatario(envio.getDestinatarioId());
            if (destinatario.isEmpty()) {
                envio.setEstado(EstadoEnvioNotificacion.OMITIDO);
                log.info("Envío {} OMITIDO: perfil desactivado o sin correo", envio.getId());
                return;
            }
            para = destinatario.get().correo();
            contenido = m.construir(notificacion, destinatario.get());
        }

        try {
            String idProveedor = correoSender.enviar(new CorreoSaliente(propiedades.getMailFrom(), para,
                    contenido.asunto(), contenido.cuerpo()));
            envio.setIntentos(envio.getIntentos() + 1);
            envio.setEstado(EstadoEnvioNotificacion.ENVIADO);
            envio.setEnviadoAt(ahora);
            envio.setUltimoError(null);
            envio.setProveedorMensajeId(recortar(idProveedor));
            // Solo ids internos: sin destinatario, nombre ni contenido.
            log.info("Envío {} ENVIADO ({}, intento {})", envio.getId(), envio.getTipoDestinatario(), envio.getIntentos());
        } catch (CorreoException e) {
            registrarFallo(envio, e.getMotivo().texto(), e.getMotivo().reintentable(), ahora);
        }
    }

    /** Criterio 8: reintenta con espera creciente o deja el envío FALLIDO con su motivo. */
    void registrarFallo(NotificacionEnvio envio, String motivo, boolean reintentable, Instant ahora) {
        int intentos = envio.getIntentos() + 1;
        envio.setIntentos(intentos);
        envio.setUltimoError(motivo);
        if (!reintentable || intentos >= Math.max(1, propiedades.getMaxIntentos())) {
            envio.setEstado(EstadoEnvioNotificacion.FALLIDO);
            log.warn("Envío {} FALLIDO tras {} intento(s): {}", envio.getId(), intentos, motivo);
            return;
        }
        envio.setEstado(EstadoEnvioNotificacion.PENDIENTE);
        envio.setProximoIntento(ahora.plus(espera(intentos)));
        log.warn("Envío {} falló (intento {} de {}): {}. Se reintentará.", envio.getId(), intentos,
                propiedades.getMaxIntentos(), motivo);
    }

    /** Espera después del intento fallido n (1, 2, …); si faltan valores, usa el último. */
    Duration espera(int intentoFallido) {
        List<Integer> minutos = propiedades.getReintentosMinutos();
        if (minutos == null || minutos.isEmpty()) {
            return Duration.ofMinutes(5);
        }
        int indice = Math.min(intentoFallido, minutos.size()) - 1;
        return Duration.ofMinutes(Math.max(1, minutos.get(Math.max(0, indice))));
    }

    private Optional<ManejadorNotificacion> manejador(TipoEventoNotificacion tipo) {
        return manejadores.stream().filter(m -> m.atiende(tipo)).findFirst();
    }

    private static NotificacionEnvio nuevoEnvio(Notificacion n, TipoDestinatario tipo, Long destinatarioId,
            Instant ahora) {
        return NotificacionEnvio.builder()
                .notificacionId(n.getId())
                .tipoDestinatario(tipo)
                .destinatarioId(destinatarioId)
                .estado(EstadoEnvioNotificacion.PENDIENTE)
                .intentos(0)
                .proximoIntento(ahora)
                .createdAt(ahora)
                .updatedAt(ahora)
                .build();
    }

    private static String recortar(String id) {
        if (id == null) {
            return null;
        }
        return id.length() <= MAX_ID_PROVEEDOR ? id : id.substring(0, MAX_ID_PROVEEDOR);
    }
}
