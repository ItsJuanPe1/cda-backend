package co.edu.univalle.palmira.service.correo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** HU-038: mensaje MIME para Gmail API. Datos ficticios. */
class CorreoMimeTest {

    private static final CorreoSaliente CORREO = new CorreoSaliente(
            "notificaciones.prueba@correounivalle.edu.co", "ana.prueba@correounivalle.edu.co",
            "Plazo abierto para registrar su disponibilidad – 2027-1 Período con tildes y ñ",
            "Hola, Ana:\nSe abrió el plazo del período.\n");

    @Test
    @DisplayName("El campo raw es base64url y contiene el mensaje completo")
    void rawEsBase64Url() {
        String raw = CorreoMime.construirRaw(CORREO);
        assertFalse(raw.contains("+") || raw.contains("/") || raw.contains("="));
        String mime = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.US_ASCII);
        assertEquals(CorreoMime.construir(CORREO), mime);
    }

    @Test
    @DisplayName("El asunto con tildes se codifica en UTF-8 (RFC 2047) y se recupera igual")
    void asuntoUtf8() {
        String mime = CorreoMime.construir(CORREO);
        Matcher m = Pattern.compile("=\\?UTF-8\\?B\\?([A-Za-z0-9+/=]+)\\?=").matcher(mime);
        StringBuilder asunto = new StringBuilder();
        while (m.find()) {
            asunto.append(new String(Base64.getDecoder().decode(m.group(1)), StandardCharsets.UTF_8));
        }
        assertEquals(CORREO.asunto(), asunto.toString());
        for (String linea : mime.split("\r\n")) {
            assertTrue(linea.length() <= 78, "línea demasiado larga: " + linea.length());
        }
    }

    @Test
    @DisplayName("El cuerpo va en UTF-8 y conserva tildes y ñ")
    void cuerpoUtf8() {
        String mime = CorreoMime.construir(CORREO);
        String cuerpoB64 = mime.substring(mime.indexOf("\r\n\r\n") + 4).replace("\r\n", "");
        String cuerpo = new String(Base64.getDecoder().decode(cuerpoB64), StandardCharsets.UTF_8);
        assertEquals("Hola, Ana:\r\nSe abrió el plazo del período.\r\n", cuerpo);
        assertTrue(mime.contains("Content-Type: text/plain; charset=UTF-8"));
    }

    @Test
    @DisplayName("No se pueden inyectar encabezados con saltos de línea")
    void sinInyeccionDeEncabezados() {
        CorreoSaliente malicioso = new CorreoSaliente("a@correounivalle.edu.co",
                "b@correounivalle.edu.co\r\nBcc: otro@ejemplo.com", "Asunto", "Cuerpo");
        String mime = CorreoMime.construir(malicioso);
        assertFalse(mime.contains("\r\nBcc:"));
    }
}
