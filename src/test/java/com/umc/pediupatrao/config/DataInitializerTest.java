package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;

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

    @ParameterizedTest
    @ValueSource(strings = {"gerente", "atendente"})
    void adminConfiguradoPrecedeUsuariosDemo(String username) {
        Map<String, Usuario> usuarios = new HashMap<>();
        when(repo.findAll()).thenAnswer(invocacao -> new ArrayList<>(usuarios.values()));
        when(repo.findByUsername(any())).thenAnswer(invocacao -> Optional.ofNullable(usuarios.get(invocacao.getArgument(0))));
        when(repo.save(any())).thenAnswer(invocacao -> {
            Usuario usuario = invocacao.getArgument(0);
            usuarios.put(usuario.getUsername(), usuario);
            return usuario;
        });
        when(encoder.encode(any())).thenAnswer(invocacao -> "hash:" + invocacao.getArgument(0));
        PedidoRepository pedidos = mock(PedidoRepository.class);
        when(pedidos.count()).thenReturn(1L);

        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("demo"))
                .withPropertyValues("app.admin.username=" + username, "app.admin.password=senha-configurada")
                .withUserConfiguration(DataInitializer.class, DemoDataSeeder.class)
                .withBean(UsuarioRepository.class, () -> repo)
                .withBean(PasswordEncoder.class, () -> encoder)
                .withBean(PedidoRepository.class, () -> pedidos)
                .withBean(ClienteRepository.class, () -> mock(ClienteRepository.class))
                .withBean(ProdutoRepository.class, () -> mock(ProdutoRepository.class))
                .run(context -> {
                    for (CommandLineRunner runner : context.getBeanProvider(CommandLineRunner.class).orderedStream().toList()) {
                        runner.run();
                    }
                    assertEquals("ADMIN", usuarios.get(username).getRole());
                    assertEquals("hash:senha-configurada", usuarios.get(username).getPassword());
                    assertEquals(2, usuarios.size());
                });
    }
}
