package com.example.booking_service.controller;

import com.example.booking_service.model.User;
import com.example.booking_service.repository.UserRepository;
import com.example.booking_service.security.DbUserDetailsService;
import com.example.booking_service.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/user")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          DbUserDetailsService uds,
                          JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(uds);
        provider.setPasswordEncoder(passwordEncoder);
        this.authManager = new ProviderManager(provider);
    }

    public static class RegisterRequest {
        public String username;
        public String password;
        public String role;
    }

    public static class AuthRequest {
        public String username;
        public String password;
    }

    public static class TokenResponse {
        public String token;
        public TokenResponse(String token) { this.token = token; }
    }

    @PostMapping("/register")
    public TokenResponse register(@RequestBody RegisterRequest req) {
        if (req.username == null || req.username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required");
        }
        if (req.password == null || req.password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required");
        }

        if (userRepository.findByUsername(req.username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username already exists");
        }

        User u = new User();
        u.setUsername(req.username);
        u.setPassword(passwordEncoder.encode(req.password));
        u.setRole((req.role == null || req.role.isBlank()) ? "USER" : req.role.trim());

        userRepository.save(u);

        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username, req.password)
        );

        String token = jwtService.generateToken(auth.getName(), auth.getAuthorities());
        return new TokenResponse(token);
    }

    @PostMapping("/auth")
    public TokenResponse auth(@RequestBody AuthRequest req) {
        if (req.username == null || req.username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required");
        }
        if (req.password == null || req.password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required");
        }

        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username, req.password)
        );

        String token = jwtService.generateToken(auth.getName(), auth.getAuthorities());
        return new TokenResponse(token);
    }
}
