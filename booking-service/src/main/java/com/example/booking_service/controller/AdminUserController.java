package com.example.booking_service.controller;

import com.example.booking_service.model.User;
import com.example.booking_service.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/user")
public class AdminUserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public static class CreateUserRequest {
        private String username;
        private String password;
        private String role;

        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getRole() { return role; }

        public void setUsername(String username) { this.username = username; }
        public void setPassword(String password) { this.password = password; }
        public void setRole(String role) { this.role = role; }
    }

    public static class UpdateUserRequest {
        private String password;
        private String role;

        public String getPassword() { return password; }
        public String getRole() { return role; }

        public void setPassword(String password) { this.password = password; }
        public void setRole(String role) { this.role = role; }
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public User createUser(@RequestBody CreateUserRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required");
        }

        String role = (req.getRole() == null || req.getRole().isBlank()) ? "USER" : req.getRole().trim();

        if (!role.equals("USER") && !role.equals("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role must be USER or ADMIN");
        }

        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username already exists");
        }

        User u = new User();
        u.setUsername(req.getUsername());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setRole(role);

        return userRepository.save(u);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public User updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest req) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            u.setPassword(passwordEncoder.encode(req.getPassword()));
        }

        if (req.getRole() != null && !req.getRole().isBlank()) {
            String role = req.getRole().trim();
            if (!role.equals("USER") && !role.equals("ADMIN")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role must be USER or ADMIN");
            }
            u.setRole(role);
        }

        return userRepository.save(u);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        userRepository.deleteById(id);
    }
}
