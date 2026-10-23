package co.edu.univalle.palmira.constant;

public final class AuthConstants {
    private AuthConstants() {
        // Utility class
    }

    public static final String DOMAIN = "@correounivalle.edu.co";
    public static final String HD_CLAIM = "correounivalle.edu.co";
    public static final int ROL_ADMIN = 1;
    public static final int ROL_COORDINADOR = 2;
    public static final int ROL_DOCENTE = 3;

    // Mensajes literales de la HU-001
    public static final String ERROR_LOGIN_FALLIDO = "No fue posible iniciar sesión. Intente de nuevo";
    public static final String ERROR_DOMINIO_INSTITUCIONAL = "Debe usar su correo institucional";
    public static final String ERROR_CUENTA_NO_REGISTRADA = "Su cuenta no está registrada. Contacte al administrador";
    public static final String ERROR_CUENTA_DESACTIVADA = "Su cuenta está desactivada";
    public static final String ERROR_SIN_PERMISOS_ADMIN = "No tiene permisos de administrador";
    public static final String ERROR_SIN_PERMISOS_DOCENTE = "No tiene permisos de Docente";
    public static final String LOGIN_SUCCESS_MESSAGE = "Inicio de sesión exitoso";
}
