package com.hopin.controller;

import com.hopin.model.Role;
import com.hopin.model.User;
import com.hopin.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record RegisterReq(@NotBlank String name, @NotBlank @Email String email,
                              @NotBlank @Size(min = 6) String password, @NotNull Role role) {
    }

    public record LoginReq(@NotBlank String email, @NotBlank String password) {
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterReq r) {
        return auth.register(r.name(), r.email(), r.password(), r.role());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginReq r) {
        return auth.login(r.email(), r.password());
    }
}
