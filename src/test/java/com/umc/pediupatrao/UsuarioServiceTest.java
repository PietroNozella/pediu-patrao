package com.umc.pediupatrao;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository repo;

    private UsuarioService service;

    @BeforeEach
    void setup() {
        service = new UsuarioService(repo, new BCryptPasswordEncoder());
    }

    private Usuario novo(String username, String role) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setPassword("senha123");
        u.setRole(role);
        return u;
    }

    @Test
    void criaUsuarioComCadaPerfilValido() {
        when(repo.findByUsername(any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        for (String role : new String[]{"ADMIN", "GERENTE", "ATENDENTE"}) {
            Usuario salvo = service.salvarUsuario(novo("user-" + role, role));
            assertEquals(role, salvo.getRole());
        }
    }

    @Test
    void rejeitaPerfilAusente() {
        Usuario u = novo("sem-perfil", null);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.salvarUsuario(u));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void rejeitaPerfilEmBranco() {
        Usuario u = novo("branco", "   ");
        assertThrows(ResponseStatusException.class, () -> service.salvarUsuario(u));
    }

    @Test
    void rejeitaPerfilInvalido() {
        Usuario u = novo("invalido", "SUPER");
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.salvarUsuario(u));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void rejeitaRoleComPrefixo() {
        Usuario u = novo("prefixado", "ROLE_ADMIN");
        assertThrows(ResponseStatusException.class, () -> service.salvarUsuario(u));
    }

    @Test
    void rejeitaUserLegadoNaCriacao() {
        Usuario u = novo("legado", "USER");
        assertThrows(ResponseStatusException.class, () -> service.salvarUsuario(u));
    }

    @Test
    void normalizaEspacosExternos() {
        when(repo.findByUsername(any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        Usuario salvo = service.salvarUsuario(novo("espaco", "  GERENTE "));
        assertEquals("GERENTE", salvo.getRole());
    }

    @Test
    void edicaoSemSenhaMantemSenhaAtual() {
        Usuario existente = novo("alvo", "ATENDENTE");
        existente.setId("1");
        existente.setPassword("HASH_ANTIGO");
        when(repo.findById("1")).thenReturn(Optional.of(existente));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        Usuario alteracao = new Usuario();
        alteracao.setRole("GERENTE");
        alteracao.setPassword("");

        Usuario atualizado = service.atualizarUsuario("1", alteracao);
        assertEquals("GERENTE", atualizado.getRole());
        assertEquals("HASH_ANTIGO", atualizado.getPassword());
    }

    @Test
    void edicaoComPerfilInvalidoRecebe400() {
        Usuario existente = novo("alvo", "ATENDENTE");
        existente.setId("1");
        when(repo.findById("1")).thenReturn(Optional.of(existente));

        Usuario alteracao = new Usuario();
        alteracao.setRole("ROLE_GERENTE");

        assertThrows(ResponseStatusException.class,
                () -> service.atualizarUsuario("1", alteracao));
    }
}
