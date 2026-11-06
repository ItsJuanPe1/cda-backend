package co.edu.univalle.palmira.model.enums;

/** HU-058 criterio 2: motivo por el que se rechazó un ingreso. */
public enum MotivoRechazoIngreso {
    TOKEN_INVALIDO,
    DOMINIO_NO_INSTITUCIONAL,
    CUENTA_NO_REGISTRADA,
    SIN_PERFIL_ROL,
    PERFIL_DESACTIVADO,
    /** RN-15: definido, pero se empieza a usar cuando se implemente la validación de ventanas (HU-055). */
    FUERA_DE_VENTANA
}
