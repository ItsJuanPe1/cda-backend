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

    public static final String AUTHORITY_ADMIN = "ROLE_ADMIN";
    public static final String AUTHORITY_COORDINADOR = "ROLE_COORDINADOR";
    public static final String AUTHORITY_DOCENTE = "ROLE_DOCENTE";

    // Mensajes literales de la HU-001 y HU-010
    public static final String ERROR_LOGIN_FALLIDO = "No fue posible iniciar sesión. Intente de nuevo";
    public static final String ERROR_DOMINIO_INSTITUCIONAL = "Debe usar su correo institucional";
    public static final String ERROR_CUENTA_NO_REGISTRADA = "Su cuenta no está registrada. Contacte al administrador";
    public static final String ERROR_CUENTA_DESACTIVADA = "Su cuenta está desactivada";
    public static final String ERROR_PERFIL_DESACTIVADO = "Su perfil está desactivado. Contacte al administrador";
    public static final String ERROR_SIN_PERMISOS_ADMIN = "No tiene permisos de administrador";
    public static final String ERROR_SIN_PERFIL_COORDINADOR = "No tiene perfil de coordinador";
    public static final String WARN_SIN_PROGRAMA_ASIGNADO = "Aún no tiene un programa asignado. Contacte al administrador";
    public static final String LOGIN_SUCCESS_MESSAGE = "Inicio de sesión exitoso";
}
