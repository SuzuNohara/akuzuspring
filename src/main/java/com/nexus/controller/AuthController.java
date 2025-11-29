package com.nexus.controller;

import com.nexus.dto.*;
import com.nexus.entity.User;
import com.nexus.service.UserService;
import com.nexus.service.PreferenceService;
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
    private final PreferenceService preferenceService;
    
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Recibida solicitud de registro para email: {}", request.getEmail());
        
        RegisterResponse response = userService.registerUser(request);
        
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Recibida solicitud de inicio de sesión para: {}", request.getEmail());
        
        try {
            User user = userService.login(request.getEmail(), request.getPassword());
            
            // Verificar si completó el cuestionario
            boolean questionnaireCompleted = preferenceService.hasCompletedQuestionnaire(user.getId());
            
            LoginResponse response = LoginResponse.builder()
                .success(true)
                .message("Inicio de sesión exitoso")
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .nickname(user.getNickname())
                .linkCode(user.getLinkCode())
                .emailConfirmed(user.getEmailConfirmed())
                .questionnaireCompleted(questionnaireCompleted)
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            // Manejo especial para cuenta no verificada (FA03)
            if (e.getMessage().equals("ACCOUNT_NOT_VERIFIED")) {
                LoginResponse response = LoginResponse.builder()
                    .success(false)
                    .message("Debes verificar tu correo electrónico antes de iniciar sesión")
                    .email(request.getEmail())
                    .emailConfirmed(false)
                    .build();
                
                return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(response);
            }
            
            // Otros errores (FA01, FA02, FA04, FA05)
            throw e;
        }
    }
    
    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        log.info("Recibida solicitud de verificación de email para: {}", request.getEmail());
        
        User user = userService.verifyEmail(request.getEmail(), request.getCode());
        
        VerifyEmailResponse response = VerifyEmailResponse.builder()
            .success(true)
            .message("Email verificado exitosamente. Tu cuenta está ahora activa.")
            .userId(user.getId())
            .email(user.getEmail())
            .displayName(user.getDisplayName())
            .nickname(user.getNickname())
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
    
    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("Solicitud de recuperación de contraseña para: {}", request.getEmail());
        
        userService.forgotPassword(request.getEmail());
        
        ForgotPasswordResponse response = ForgotPasswordResponse.builder()
            .success(true)
            .message("Si el correo está registrado, recibirás un código de recuperación")
            .email(request.getEmail())
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/verify-reset-code")
    public ResponseEntity<VerifyEmailResponse> verifyResetCode(@Valid @RequestBody VerifyEmailRequest request) {
        log.info("Solicitud de verificación de código de recuperación para: {}", request.getEmail());
        
        String status = userService.verifyResetCode(request.getEmail(), request.getCode());
        
        VerifyEmailResponse response = VerifyEmailResponse.builder()
            .success(true)
            .message(status.equals("EXPIRED_NEW_CODE_SENT") 
                ? "El código había expirado. Se ha enviado un nuevo código a tu correo."
                : "Código verificado exitosamente")
            .email(request.getEmail())
            .emailConfirmed(true)
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/resend-reset-code")
    public ResponseEntity<String> resendResetCode(@RequestParam String email) {
        log.info("Solicitud de reenvío de código de recuperación para: {}", email);
        
        userService.forgotPassword(email);
        
        return ResponseEntity.ok("Nuevo código de recuperación enviado exitosamente");
    }
    
    @PostMapping("/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("Solicitud de restablecimiento de contraseña para: {}", request.getEmail());
        
        userService.resetPassword(
            request.getEmail(), 
            request.getCode(), 
            request.getNewPassword(), 
            request.getConfirmPassword()
        );
        
        ResetPasswordResponse response = ResetPasswordResponse.builder()
            .success(true)
            .message("Contraseña restablecida exitosamente")
            .email(request.getEmail())
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Nexus API is running");
    }
}
