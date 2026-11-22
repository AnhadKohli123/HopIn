package com.hopin.security;

import com.hopin.exception.UnauthorizedActionException;
import com.hopin.model.User;
import com.hopin.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final JwtUtil jwt;
    private final UserRepository users;

    public AuthInterceptor(JwtUtil jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            return true;
        }
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new UnauthorizedActionException("Login required");
        }
        User user;
        try {
            Long id = jwt.parseUserId(header.substring(7));
            user = users.findById(id).orElseThrow(() -> new UnauthorizedActionException("User no longer exists"));
        } catch (UnauthorizedActionException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedActionException("Invalid or expired token");
        }
        if (user.blocked) {
            throw new UnauthorizedActionException("Your account has been blocked");
        }
        req.setAttribute("user", user);
        return true;
    }
}
