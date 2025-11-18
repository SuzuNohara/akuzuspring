package com.nexus.service;

import com.nexus.dto.RegisterRequest;
import com.nexus.dto.RegisterResponse;
import com.nexus.entity.User;
import com.nexus.entity.UserProfile;
import com.nexus.entity.VerificationToken;
import com.nexus.exception.BadRequestException;
import com.nexus.repository.UserRepository;
import com.nexus.repository.VerificationTokenRepository;
import com.nexus.util.LinkCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Random;
import java.util.regex.Pattern;
import com.nexus.model.AccountStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    // private final EmailService emailService; // Lo implementaremos después
    
    @Transactional
    public RegisterResponse registerUser(RegisterRequest request) {
        log.info("Iniciando registro de usuario para email: {}", request.getEmail());
        
        // Validaciones
        validateRegistrationRequest(request);
        
        // Verificar que el email no esté en uso
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("El email ya está registrado");
        }
        
        // Generar link code único
        String linkCode = generateUniqueLinkCode();
        
        // Crear usuario
        User user = User.builder()
            .email(request.getEmail().toLowerCase().trim())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .displayName(request.getDisplayName().trim())
            .nickname(request.getNickname() != null ? request.getNickname().trim() : null)
            .birthDate(request.getBirthDate())
            .linkCode(linkCode)
            .linkCodeVersion(1)
            .linkCodeUpdatedAt(Instant.now())
            .emailConfirmed(false)
            .accountStatus(AccountStatus.PENDING_VERIFICATION)
            .termsAccepted(request.isTermsAccepted())
            .termsAcceptedAt(request.isTermsAccepted() ? Instant.now() : null)
            .build();
        
        // Guardar usuario
        user = userRepository.save(user);
        log.info("Usuario creado con ID: {}", user.getId());
        
        // Crear perfil vacío
        UserProfile profile = UserProfile.builder()
            .user(user)
            .userId(user.getId())
            .build();
        user.setProfile(profile);
        
        // Crear token de verificación de email
        VerificationToken verificationToken = createEmailVerificationToken(user);
        
        // TODO: Enviar email de verificación
        // emailService.sendVerificationEmail(user.getEmail(), verificationToken.getToken());
        
        log.info("Registro completado exitosamente para usuario: {}", user.getEmail());
        
        return RegisterResponse.builder()
            .userId(user.getId())
            .email(user.getEmail())
            .displayName(user.getDisplayName())
            .nickname(user.getNickname())
            .linkCode(user.getLinkCode())
            .emailConfirmed(user.getEmailConfirmed())
            .createdAt(user.getCreatedAt())
            .message("Registro exitoso. Por favor verifica tu email.")
            .build();
    }
    
    private void validateRegistrationRequest(RegisterRequest request) {
        // Validar que las contraseñas coincidan
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Las contraseñas no coinciden");
        }
        
        // Validar edad mínima (18 años) RN-23
        LocalDate now = LocalDate.now();
        Period age = Period.between(request.getBirthDate(), now);
        if (age.getYears() < 18) {
            throw new BadRequestException("Debes tener al menos 18 años para registrarte");
        }
        
        // Validar que la fecha de nacimiento no sea futura
        if (request.getBirthDate().isAfter(now)) {
            throw new BadRequestException("La fecha de nacimiento no puede ser en el futuro");
        }
        // Validar formato de email (RFC 5322 simplificado) RN-24
        String emailRegex = "^(?=.{1,64}@)[A-Za-z0-9_+.-]+(\\.[A-Za-z0-9_+.-]+)*@[^-][A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$";
        if (!Pattern.compile(emailRegex).matcher(request.getEmail()).matches()) {
            throw new BadRequestException("El formato del email no es válido (RFC 5322)");
        }

        // Validar política de contraseña segura RN-01 (ya anotaciones, reforzamos special char)
        String passwordPolicy = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-\\[\\]{};':\\\"\\\\|,.<>/?]).{8,}$";
        if (!Pattern.compile(passwordPolicy).matcher(request.getPassword()).matches()) {
            throw new BadRequestException("La contraseña no cumple la política de seguridad");
        }

        // Términos aceptados (simulado) RN flujo paso 4
        if (!request.isTermsAccepted()) {
            throw new BadRequestException("Debes aceptar los términos y condiciones");
        }
    }
    
    private String generateUniqueLinkCode() {
        String linkCode;
        int attempts = 0;
        int maxAttempts = 10;
        
        do {
            linkCode = LinkCodeGenerator.generate();
            attempts++;
            
            if (attempts >= maxAttempts) {
                throw new RuntimeException("No se pudo generar un código de enlace único");
            }
        } while (userRepository.existsByLinkCode(linkCode));
        
        log.debug("Link code generado: {} en {} intento(s)", linkCode, attempts);
        return linkCode;
    }
    
    private VerificationToken createEmailVerificationToken(User user) {
        String token = generateVerificationCode(); // Código corto RN-02
        
        VerificationToken verificationToken = VerificationToken.builder()
            .user(user)
            .token(token)
            .purpose(VerificationToken.TokenPurpose.EMAIL_CONFIRM)
            .expiresAt(Instant.now().plusSeconds(3600)) // 1 hora RN-02
            .build();
        
        return verificationTokenRepository.save(verificationToken);
    }

    private String generateVerificationCode() {
        // 6 dígitos numéricos
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}
