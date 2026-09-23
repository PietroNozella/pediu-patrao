package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.PerfilUsuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return usuarioRepository.findByUsername(username)
                .map(usuario -> {
                    final PerfilUsuario perfil;
                    try {
                        perfil = PerfilUsuario.validar(usuario.getRole());
                    } catch (IllegalArgumentException e) {
                        throw new UsernameNotFoundException("Perfil invalido para o usuario: " + username);
                    }
                    return new User(usuario.getUsername(), usuario.getPassword(),
                            List.of(new SimpleGrantedAuthority(perfil.authority())));
                })
                .orElseThrow(() -> new UsernameNotFoundException("Usuario nao encontrado: " + username));
    }
}
