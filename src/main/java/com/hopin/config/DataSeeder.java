package com.hopin.config;

import com.hopin.model.Role;
import com.hopin.model.User;
import com.hopin.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CommandLineRunner seedAdmin(UserRepository users, PasswordEncoder encoder,
                                       @Value("${hopin.admin.password}") String adminPassword) {
        return args -> {
            if (users.findByEmail("admin@hopin.com").isEmpty()) {
                User a = new User();
                a.name = "Admin";
                a.email = "admin@hopin.com";
                a.passwordHash = encoder.encode(adminPassword);
                a.role = Role.ADMIN;
                users.save(a);
            }
        };
    }
}
