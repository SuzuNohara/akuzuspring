package com.nexus.controller;

import com.nexus.dto.RegisterRequest;
import com.nexus.dto.RegisterResponse;
import com.nexus.dto.VerifyEmailRequest;
import com.nexus.dto.VerifyEmailResponse;
import com.nexus.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {
    
    private final UserService userService;
    
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Recibida solicitud de registro para email: {}", request.getEmail());
        
        RegisterResponse response = userService.registerUser(request);
        
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }
    
    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        log.info("Recibida solicitud de verificación de email para: {}", request.getEmail());
        
        userService.verifyEmail(request.getEmail(), request.getCode());
        
        VerifyEmailResponse response = VerifyEmailResponse.builder()
            .success(true)
            .message("Email verificado exitosamente. Tu cuenta está ahora activa.")
            .email(request.getEmail())
            .emailConfirmed(true)
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/resend-verification")
    public ResponseEntity<String> resendVerification(@RequestParam String email) {
        log.info("Solicitud de reenvío de código de verificación para: {}", email);
        
        userService.resendVerificationCode(email);
        
        return ResponseEntity.ok("Código de verificación reenviado exitosamente");
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Nexus API is running");
    }
}
