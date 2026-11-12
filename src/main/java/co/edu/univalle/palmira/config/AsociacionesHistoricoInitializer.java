package co.edu.univalle.palmira.config;

import co.edu.univalle.palmira.service.DocenteAsignaturaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AsociacionesHistoricoInitializer {

    private final DocenteAsignaturaService docenteAsignaturaService;

    @EventListener(ApplicationReadyEvent.class)
    public void sincronizarHistorico() {
        try {
            docenteAsignaturaService.sincronizarDesdeHistorico();
        } catch (Exception ex) {
            log.error("HU-049: no se pudo sincronizar el histórico de asociaciones al iniciar", ex);
        }
    }
}
