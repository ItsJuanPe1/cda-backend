package co.edu.univalle.palmira.dto;

import co.edu.univalle.palmira.model.enums.AccionBitacora;
import co.edu.univalle.palmira.model.enums.MotivoRechazoIngreso;
import co.edu.univalle.palmira.model.enums.ResultadoIngreso;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public class BitacoraDto {

    /** HU-058: un registro de la bitácora. La fecha va en UTC. */
    public record Response(
            Long id,
            Instant fecha,
            String usuarioCorreo,
            String usuarioRol,
            String operadorNombre,
            AccionBitacora accion,
            String entidad,
            String entidadId,
            Long periodoId,
            JsonNode valorAnterior,
            JsonNode valorNuevo,
            ResultadoIngreso resultado,
            MotivoRechazoIngreso motivo
    ) {}

    /** HU-058 criterio 3: página de resultados, del más reciente al más antiguo. */
    public record Pagina(
            List<Response> contenido,
            int pagina,
            int tamano,
            long totalElementos,
            int totalPaginas
    ) {}
}
