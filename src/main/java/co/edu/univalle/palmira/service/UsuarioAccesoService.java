package co.edu.univalle.palmira.service;

import co.edu.univalle.palmira.model.entity.Usuario;
import co.edu.univalle.palmira.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Mantiene la tabla {@code usuarios} (todos los usuarios de la app) al día cuando se crea o edita un
 * docente o un coordinador. Se llama dentro de la misma transacción del servicio que guarda el perfil,
 * así que si algo falla no queda un perfil sin su usuario ni al revés.
 * <p>
 * Cada perfil tiene su propio correo institucional, y por tanto su propia fila: el rol se fija al crearla
 * y no cambia. La fila se busca por el correo institucional.
 */
@Service
@RequiredArgsConstructor
public class UsuarioAccesoService {

    private final UsuarioRepository usuarioRepository;

    /**
     * Crea o actualiza el usuario de un coordinador. El nombre completo se separa en nombre y apellidos
     * (ver {@link #dividirNombre}).
     *
     * @param correoAnterior correo institucional antes de la edición (null si es nuevo); así, si el correo
     *                       cambia, se actualiza la misma fila en vez de crear otra
     */
    @Transactional
    public Usuario sincronizar(String correoAnterior, String nombreCompleto, String correoInstitucional,
                               String correoPersonal, String celular, int rol, boolean activo) {
        String[] partes = dividirNombre(nombreCompleto);
        return sincronizar(correoAnterior, partes[0], partes[1], correoInstitucional, correoPersonal, celular,
                rol, activo);
    }

    /** Variante para el docente, que ya trae nombres y apellidos por separado. */
    @Transactional
    public Usuario sincronizar(String correoAnterior, String nombres, String apellidos, String correoInstitucional,
                               String correoPersonal, String celular, int rol, boolean activo) {
        String email = normalizar(correoInstitucional);
        if (email == null) {
            return null;
        }

        Optional<Usuario> existente = Optional.empty();
        if (correoAnterior != null && !correoAnterior.isBlank()) {
            existente = usuarioRepository.buscarPorCorreoInstitucional(correoAnterior.trim());
        }
        if (existente.isEmpty()) {
            existente = usuarioRepository.buscarPorCorreoInstitucional(email);
        }

        Usuario usuario = existente.orElseGet(() -> Usuario.builder().rol(rol).build());
        usuario.setNombre(recortar(vacioA(nombres, "-")));
        usuario.setApellido(recortar(vacioA(apellidos, "-")));
        usuario.setEmailInstitucional(email);
        usuario.setEmailPersonal(normalizar(correoPersonal)); // opcional: sin correo personal queda vacío
        usuario.setCelular(soloDigitos(celular));
        return usuarioRepository.save(usuario);
    }

    /**
     * Conservado por compatibilidad; el estado activo/inactivo vive en docentes.estado y coordinadores.activo
     * (usuarios.activo fue eliminada en V47).
     */
    @Transactional
    public void cambiarEstado(String correoInstitucional, boolean activo) {
        // Sin efecto en usuarios: el estado vive en el perfil correspondiente.
    }

    /**
     * Los nombres completos son de 2 nombres y 2 apellidos, o 1 nombre y 2 apellidos:
     * 4 o más palabras → los dos últimos términos son los apellidos; 3 → el primero es el nombre y los
     * otros dos los apellidos; 2 → nombre y apellido; 1 → solo nombre.
     */
    static String[] dividirNombre(String completo) {
        String limpio = completo == null ? "" : completo.trim().replaceAll("\\s+", " ");
        if (limpio.isEmpty()) {
            return new String[]{"", ""};
        }
        String[] palabras = limpio.split(" ");
        int apellidos = palabras.length >= 3 ? 2 : palabras.length == 2 ? 1 : 0;
        int corte = palabras.length - apellidos;
        return new String[]{
                String.join(" ", java.util.Arrays.copyOfRange(palabras, 0, corte)),
                String.join(" ", java.util.Arrays.copyOfRange(palabras, corte, palabras.length))
        };
    }

    private static String soloDigitos(String celular) {
        if (celular == null) {
            return null;
        }
        String digitos = celular.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    private static String vacioA(String texto, String porDefecto) {
        return texto == null || texto.isBlank() ? porDefecto : texto.trim();
    }

    private static String normalizar(String correo) {
        return correo == null || correo.isBlank() ? null : correo.trim().toLowerCase(Locale.ROOT);
    }

    private static String recortar(String texto) {
        return texto.length() > 150 ? texto.substring(0, 150) : texto;
    }
}
