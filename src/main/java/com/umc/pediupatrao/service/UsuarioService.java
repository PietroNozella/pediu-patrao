package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    public Usuario salvarUsuario(Usuario usuario) {
        if (usuario.getUsername() == null || usuario.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username obrigatorio");
        }
        usuarioRepository.findByUsername(usuario.getUsername()).ifPresent(u -> {
            if (usuario.getId() == null || !u.getId().equals(usuario.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username ja existe");
            }
        });
        if (usuario.getPassword() == null || usuario.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha obrigatoria");
        }
        if (usuario.getRole() == null || usuario.getRole().isBlank()) {
            usuario.setRole("USER");
        }
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        return usuarioRepository.save(usuario);
    }

    public void deletarUsuario(String id) {
        if (!usuarioRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado");
        }
        usuarioRepository.deleteById(id);
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario atualizarUsuario(String id, Usuario usuarioAtualizado) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado"));
        if (usuarioAtualizado.getUsername() != null && !usuarioAtualizado.getUsername().isBlank()) {
            usuarioRepository.findByUsername(usuarioAtualizado.getUsername()).ifPresent(u -> {
                if (!u.getId().equals(id)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Username ja existe");
                }
            });
            existente.setUsername(usuarioAtualizado.getUsername());
        }
        if (usuarioAtualizado.getRole() != null && !usuarioAtualizado.getRole().isBlank()) {
            existente.setRole(usuarioAtualizado.getRole());
        }
        if (usuarioAtualizado.getPassword() != null && !usuarioAtualizado.getPassword().isBlank()) {
            existente.setPassword(passwordEncoder.encode(usuarioAtualizado.getPassword()));
        }
        return usuarioRepository.save(existente);
    }
}
