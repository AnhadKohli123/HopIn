package com.hopin.service;

import com.hopin.exception.InvalidLoginException;
import com.hopin.exception.UnauthorizedActionException;
import com.hopin.exception.UserAlreadyExistsException;
import com.hopin.model.Role;
import com.hopin.model.User;
import com.hopin.repository.UserRepository;
import com.hopin.security.JwtUtil;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtUtil jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public Map<String, Object> register(String name, String email, String password, Role role) {
        if (role == Role.ADMIN) {
            throw new UnauthorizedActionException("Cannot register as admin");
        }
        String mail = email.trim().toLowerCase();
        if (users.findByEmail(mail).isPresent()) {
            throw new UserAlreadyExistsException(mail);
        }
        User u = new User();
        u.name = name.trim();
        u.email = mail;
        u.passwordHash = encoder.encode(password);
        u.role = role;
        users.save(u);
        return tokenFor(u);
    }

    public Map<String, Object> login(String email, String password) {
        User u = users.findByEmail(email.trim().toLowerCase()).orElseThrow(InvalidLoginException::new);
        if (!encoder.matches(password, u.passwordHash)) {
            throw new InvalidLoginException();
        }
        if (u.blocked) {
            throw new UnauthorizedActionException("Your account has been blocked");
        }
        return tokenFor(u);
    }

    private Map<String, Object> tokenFor(User u) {
        return Map.of("token", jwt.generate(u.id, u.role.name()), "user", Views.self(u));
    }
}
