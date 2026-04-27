package com.gmp.defect_system.controller;

import com.gmp.defect_system.dto.UpdateProfileRequest;
import com.gmp.defect_system.entity.User;
import com.gmp.defect_system.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PutMapping("/update-profile")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Gagal mengupdate profil: Sesi Anda telah habis atau Token JWT tidak terbaca. Silakan Logout dan Login kembali.");
            }

            String currentUsername = auth.getName();
            User user = userRepository.findByUsername(currentUsername)
                    .orElseThrow(() -> new Exception("User dengan ID '" + currentUsername + "' tidak ditemukan di database."));
            user.setFullName(request.getFullName());

            if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
                user.setEmail(request.getEmail());
            }
            if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
                user.setPassword(passwordEncoder.encode(request.getPassword()));
            }

            userRepository.save(user);

            return ResponseEntity.ok().body("Profil berhasil diperbarui!");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Gagal mengupdate profil: " + e.getMessage());
        }
    }
}