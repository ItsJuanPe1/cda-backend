package co.edu.univalle.palmira.service.correo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * HU-038: arma el mensaje MIME (RFC 5322 / RFC 2047) de texto plano en UTF-8 que pide
 * Gmail API en el campo {@code raw} (base64url).
 */
public final class CorreoMime {

    private static final String CRLF = "\r\n";
    /**
     * Bytes UTF-8 por palabra codificada: 39 bytes son 52 caracteres en base64, y con «Subject: » y
     * «=?UTF-8?B?…?=» la línea queda por debajo de los 78 caracteres recomendados (RFC 5322).
     */
    private static final int BYTES_POR_PALABRA = 39;

    private CorreoMime() {
    }

    /** Mensaje MIME completo, en ASCII. */
    public static String construir(CorreoSaliente correo) {
        String cuerpo = Base64.getMimeEncoder(76, CRLF.getBytes(StandardCharsets.US_ASCII))
                .encodeToString(normalizarSaltos(correo.cuerpo()).getBytes(StandardCharsets.UTF_8));
        return "From: " + encabezadoSeguro(correo.de()) + CRLF
                + "To: " + encabezadoSeguro(correo.para()) + CRLF
                + "Subject: " + codificarEncabezado(correo.asunto()) + CRLF
                + "MIME-Version: 1.0" + CRLF
                + "Content-Type: text/plain; charset=UTF-8" + CRLF
                + "Content-Transfer-Encoding: base64" + CRLF
                + CRLF
                + cuerpo + CRLF;
    }

    /** Valor del campo raw de Gmail API: el mensaje en base64url. */
    public static String construirRaw(CorreoSaliente correo) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(construir(correo).getBytes(StandardCharsets.US_ASCII));
    }

    /** RFC 2047: «=?UTF-8?B?…?=» en trozos que no parten caracteres. */
    static String codificarEncabezado(String texto) {
        String limpio = encabezadoSeguro(texto);
        List<String> palabras = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < limpio.length(); ) {
            int cp = limpio.codePointAt(i);
            String caracter = new String(Character.toChars(cp));
            int largo = caracter.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + largo > BYTES_POR_PALABRA && !actual.isEmpty()) {
                palabras.add(palabra(actual.toString()));
                actual.setLength(0);
                bytes = 0;
            }
            actual.append(caracter);
            bytes += largo;
            i += Character.charCount(cp);
        }
        if (!actual.isEmpty() || palabras.isEmpty()) {
            palabras.add(palabra(actual.toString()));
        }
        return String.join(CRLF + " ", palabras);
    }

    private static String palabra(String trozo) {
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(trozo.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    /** Quita saltos de línea para impedir que se inyecten encabezados. */
    static String encabezadoSeguro(String valor) {
        return valor == null ? "" : valor.replace("\r", " ").replace("\n", " ").trim();
    }

    private static String normalizarSaltos(String texto) {
        String t = texto == null ? "" : texto.replace("\r\n", "\n").replace("\r", "\n");
        return t.replace("\n", CRLF);
    }
}
