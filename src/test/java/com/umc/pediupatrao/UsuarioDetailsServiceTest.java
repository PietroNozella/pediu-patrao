package com.umc.pediupatrao;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioDetailsServiceTest {

    @Mock
    private UsuarioRepository repo;

    @InjectMocks
    private UsuarioDetailsService detailsService;

    private void mockUsuario(String username, String role) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setPassword("hash");
        u.setRole(role);
        when(repo.findByUsername(username)).thenReturn(Optional.of(u));
    }

    @Test
    void adminRecebeRoleAdmin() {
        mockUsuario("admin", "ADMIN");
        UserDetails details = detailsService.loadUserByUsername("admin");
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void gerenteRecebeRoleGerente() {
        mockUsuario("gerente", "GERENTE");
        UserDetails details = detailsService.loadUserByUsername("gerente");
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_GERENTE")));
    }

    @Test
    void atendenteRecebeRoleAtendente() {
        mockUsuario("atendente", "ATENDENTE");
        UserDetails details = detailsService.loadUserByUsername("atendente");
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ATENDENTE")));
    }

    @Test
    void perfilDesconhecidoNaoAutentica() {
        mockUsuario("estranho", "SUPER");
        assertThrows(UsernameNotFoundException.class,
                () -> detailsService.loadUserByUsername("estranho"));
    }

    @Test
    void usuarioLegadoUserNaoAutentica() {
        mockUsuario("legado", "USER");
        assertThrows(UsernameNotFoundException.class,
                () -> detailsService.loadUserByUsername("legado"));
    }

    @Test
    void usuarioInexistenteNaoAutentica() {
        when(repo.findByUsername("fantasma")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class,
                () -> detailsService.loadUserByUsername("fantasma"));
    }
}
