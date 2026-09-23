package com.umc.pediupatrao;

import com.umc.pediupatrao.entity.PerfilUsuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PerfilUsuarioTest {

    @Test
    void aceitaOsTresPerfis() {
        assertEquals(PerfilUsuario.ADMIN, PerfilUsuario.validar("ADMIN"));
        assertEquals(PerfilUsuario.GERENTE, PerfilUsuario.validar("GERENTE"));
        assertEquals(PerfilUsuario.ATENDENTE, PerfilUsuario.validar("ATENDENTE"));
    }

    @Test
    void ignoraEspacosExternos() {
        assertEquals(PerfilUsuario.ADMIN, PerfilUsuario.validar("  ADMIN  "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ROLE_ADMIN", "ROLE_GERENTE", "ROLE_ATENDENTE",
            "admin", "SUPER", "ADMINISTRADOR", ""})
    void rejeitaPerfisInvalidos(String perfil) {
        assertThrows(IllegalArgumentException.class, () -> PerfilUsuario.validar(perfil));
    }

    @Test
    void rejeitaPerfilNulo() {
        assertThrows(IllegalArgumentException.class, () -> PerfilUsuario.validar(null));
    }

    @Test
    void authorityUsaPrefixoRole() {
        assertEquals("ROLE_ADMIN", PerfilUsuario.ADMIN.authority());
        assertEquals("ROLE_GERENTE", PerfilUsuario.GERENTE.authority());
        assertEquals("ROLE_ATENDENTE", PerfilUsuario.ATENDENTE.authority());
    }
}
