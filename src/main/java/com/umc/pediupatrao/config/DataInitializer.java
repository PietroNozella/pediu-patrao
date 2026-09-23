package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:teste123}")
    private String adminPassword;

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, PasswordEncoder encoder) {
        return args -> {
            repo.findAll().stream()
                    .filter(u -> "USER".equals(u.getRole()))
                    .forEach(u -> {
                        u.setRole("ATENDENTE");
                        repo.save(u);
                    });
            if (repo.findByUsername(adminUsername).isEmpty()) {
                Usuario user = new Usuario();
                user.setUsername(adminUsername);
                user.setPassword(encoder.encode(adminPassword));
                user.setRole("ADMIN");
                repo.save(user);
                System.out.println("Usuario admin criado!");
            }
        };
    }
}
