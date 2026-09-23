package com.umc.pediupatrao.entity;

public enum PerfilUsuario {
    ADMIN,
    GERENTE,
    ATENDENTE;

    public String authority() {
        return "ROLE_" + name();
    }

    public static PerfilUsuario validar(String valor) {
        String normalizado = valor == null ? "" : valor.trim();
        for (PerfilUsuario perfil : values()) {
            if (perfil.name().equals(normalizado)) {
                return perfil;
            }
        }
        throw new IllegalArgumentException("Perfil invalido");
    }
}
