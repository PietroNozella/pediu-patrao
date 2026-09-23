package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private UsuarioRepository repo;

    @Mock
    private PasswordEncoder encoder;

    private DataInitializer initializer() {
        DataInitializer initializer = new DataInitializer();
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "adminPassword", "teste123");
        return initializer;
    }

    private Usuario usuario(String username, String role) {
        Usuario u = new Usuario();
        u.setId(username);
        u.setUsername(username);
        u.setPassword("hash");
        u.setRole(role);
        return u;
    }

    @Test
    void migraUserParaAtendenteSemTocarNosValidos() throws Exception {
        List<Usuario> todos = new ArrayList<>(List.of(
                usuario("legado", "USER"),
                usuario("admin", "ADMIN"),
                usuario("gerente", "GERENTE"),
                usuario("atendente", "ATENDENTE")));
        when(repo.findAll()).thenReturn(todos);
        when(repo.findByUsername("admin")).thenReturn(Optional.of(usuario("admin", "ADMIN")));

        initializer().initDatabase(repo, encoder).run();

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repo, times(1)).save(captor.capture());
        assertEquals("legado", captor.getValue().getUsername());
        assertEquals("ATENDENTE", captor.getValue().getRole());
    }

    @Test
    void migracaoEhIdempotente() throws Exception {
        List<Usuario> migrados = new ArrayList<>(List.of(usuario("legado", "ATENDENTE")));
        when(repo.findAll()).thenReturn(migrados);
        when(repo.findByUsername("admin")).thenReturn(Optional.of(usuario("admin", "ADMIN")));

        initializer().initDatabase(repo, encoder).run();

        verify(repo, never()).save(any());
    }

    @Test
    void criaAdminQuandoNaoExiste() throws Exception {
        when(repo.findAll()).thenReturn(List.of());
        when(repo.findByUsername("admin")).thenReturn(Optional.empty());
        when(encoder.encode("teste123")).thenReturn("HASH");

        initializer().initDatabase(repo, encoder).run();

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repo).save(captor.capture());
        assertEquals("admin", captor.getValue().getUsername());
        assertEquals("ADMIN", captor.getValue().getRole());
        assertEquals("HASH", captor.getValue().getPassword());
    }
}
