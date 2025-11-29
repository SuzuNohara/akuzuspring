package com.nexus.service;

import com.nexus.dto.*;
import com.nexus.entity.User;
import com.nexus.entity.UserProfile;
import com.nexus.entity.VerificationToken;
import com.nexus.exception.BadRequestException;
import com.nexus.repository.UserRepository;
import com.nexus.repository.UserProfileRepository;
import com.nexus.repository.VerificationTokenRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.repository.LinkCodeRepository;
import com.nexus.util.LinkCodeGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Base64;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;
import com.nexus.model.AccountStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final LinkRepository linkRepository;
    private final LinkCodeRepository linkCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    
    @PersistenceContext
    private EntityManager entityManager;
    
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
            .failedLoginAttempts(0)
            .build();
        
        // Guardar usuario
        user = userRepository.save(user);
        log.info("Usuario creado con ID: {}", user.getId());
        
        // Crear token de verificación de email
        VerificationToken verificationToken = createEmailVerificationToken(user);
        
        // Enviar email de verificación de forma asíncrona
        // Esto no bloqueará la respuesta del registro
        final String userEmail = user.getEmail();
        final String tokenCode = verificationToken.getToken();
        new Thread(() -> {
            try {
                emailService.sendVerificationEmail(userEmail, tokenCode);
                log.info("Email de verificación enviado a: {}", userEmail);
            } catch (Exception e) {
                log.error("Error al enviar email de verificación a {}: {}", userEmail, e.getMessage());
            }
        }).start();
        
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
    
    /**
     * Verifica el código de email y activa la cuenta del usuario
     * RN-02: El código tiene validez de 1 hora
     */
    @Transactional
    public User verifyEmail(String email, String code) {
        log.info("Verificando email para: {}", email);
        
        // Buscar usuario por email
        User user = userRepository.findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));
        
        // Verificar si ya está confirmado
        if (user.getEmailConfirmed()) {
            log.info("El email ya está verificado para: {}", email);
            return user; // No es error, simplemente ya está verificado
        }
        
        // Buscar token de verificación
        VerificationToken token = verificationTokenRepository
            .findLatestValidToken(user.getId(), VerificationToken.TokenPurpose.EMAIL_CONFIRM, Instant.now())
            .orElseThrow(() -> new BadRequestException("Código de verificación no válido o expirado"));
        
        // Validar el código
        if (!token.getToken().equals(code)) {
            throw new BadRequestException("Código de verificación incorrecto");
        }
        
        // Verificar expiración (RN-02: 1 hora)
        if (token.isExpired()) {
            throw new BadRequestException("El código de verificación ha expirado. Solicita uno nuevo.");
        }
        
        // Verificar que no haya sido usado
        if (token.isConsumed()) {
            throw new BadRequestException("Este código ya ha sido utilizado");
        }
        
        // Marcar token como consumido
        token.setConsumedAt(Instant.now());
        verificationTokenRepository.save(token);
        
        // Activar cuenta del usuario
        user.setEmailConfirmed(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user = userRepository.save(user);
        
        log.info("Email verificado exitosamente para: {}", email);
        return user;
    }
    
    /**
     * Reenvía el código de verificación de email
     */
    @Transactional
    public void resendVerificationCode(String email) {
        log.info("Reenviando código de verificación para: {}", email);
        
        // Buscar usuario por email
        User user = userRepository.findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));
        
        // Verificar si ya está confirmado
        if (user.getEmailConfirmed()) {
            throw new BadRequestException("El email ya está verificado");
        }
        
        // Invalidar tokens anteriores no consumidos
        verificationTokenRepository
            .findByUserIdAndPurposeAndConsumedAtIsNull(
                user.getId(), 
                VerificationToken.TokenPurpose.EMAIL_CONFIRM
            )
            .forEach(token -> {
                token.setConsumedAt(Instant.now());
                verificationTokenRepository.save(token);
            });
        
        // Crear nuevo token de verificación
        VerificationToken newToken = createEmailVerificationToken(user);
        
        // Enviar email de forma asíncrona
        final String userEmail = user.getEmail();
        final String tokenCode = newToken.getToken();
        new Thread(() -> {
            try {
                emailService.sendVerificationEmail(userEmail, tokenCode);
                log.info("Email de verificación reenviado a: {}", userEmail);
            } catch (Exception e) {
                log.error("Error al reenviar email de verificación a {}: {}", userEmail, e.getMessage());
            }
        }).start();
        
        log.info("Código de verificación generado para: {}", email);
    }
    
    /**
     * Inicia el proceso de recuperación de contraseña
     * RN-04: Código válido por 1 hora
     * RN-24: Validación de formato de email
     */
    @Transactional
    public void forgotPassword(String email) {
        log.info("Solicitud de recuperación de contraseña para: {}", email);
        
        // Buscar usuario por email (RN-24 ya validado por @Email en DTO)
        User user = userRepository.findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new BadRequestException("Si el correo está registrado, recibirás un código de recuperación"));
        
        // Verificar que la cuenta esté activa
        if (!user.getEmailConfirmed()) {
            throw new BadRequestException("Debes verificar tu email antes de recuperar la contraseña");
        }
        
        // Invalidar tokens anteriores de reset de contraseña no consumidos
        verificationTokenRepository
            .findByUserIdAndPurposeAndConsumedAtIsNull(
                user.getId(), 
                VerificationToken.TokenPurpose.PASSWORD_RESET
            )
            .forEach(token -> {
                token.setConsumedAt(Instant.now());
                verificationTokenRepository.save(token);
            });
        
        // Crear token de recuperación (RN-04: válido por 1 hora)
        String resetCode = generateVerificationCode();
        VerificationToken resetToken = VerificationToken.builder()
            .user(user)
            .token(resetCode)
            .purpose(VerificationToken.TokenPurpose.PASSWORD_RESET)
            .expiresAt(Instant.now().plusSeconds(3600)) // 1 hora
            .build();
        
        verificationTokenRepository.save(resetToken);
        
        // Enviar email de recuperación de forma asíncrona
        final String userEmail = user.getEmail();
        new Thread(() -> {
            try {
                emailService.sendPasswordResetEmail(userEmail, resetCode);
                log.info("Email de recuperación enviado a: {}", userEmail);
            } catch (Exception e) {
                log.error("Error al enviar email de recuperación a {}: {}", userEmail, e.getMessage());
            }
        }).start();
        
        log.info("Código de recuperación generado para: {}", email);
    }
    
    /**
     * Verifica el código de recuperación antes de restablecer la contraseña
     * RN-04: Validación de código y expiración
     * Si el código está expirado, genera uno nuevo automáticamente
     */
    @Transactional
    public String verifyResetCode(String email, String code) {
        log.info("Verificando código de recuperación para: {}", email);
        
        // Buscar usuario
        User user = userRepository.findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));
        
        // Buscar el token más reciente de recuperación (incluso si está expirado)
        Optional<VerificationToken> tokenOpt = verificationTokenRepository
            .findByUserIdAndPurposeAndConsumedAtIsNull(user.getId(), VerificationToken.TokenPurpose.PASSWORD_RESET)
            .stream()
            .filter(t -> !t.isConsumed())
            .max((t1, t2) -> t1.getCreatedAt().compareTo(t2.getCreatedAt()));
        
        if (!tokenOpt.isPresent()) {
            throw new BadRequestException("No se encontró un código de recuperación activo");
        }
        
        VerificationToken token = tokenOpt.get();
        
        // Verificar si el código está expirado ANTES de validar si coincide
        if (token.isExpired()) {
            log.info("Código expirado detectado para: {}. Generando nuevo código.", email);
            
            // Invalidar el token expirado
            token.setConsumedAt(Instant.now());
            verificationTokenRepository.save(token);
            
            // Generar nuevo código
            String newResetCode = generateVerificationCode();
            VerificationToken newToken = VerificationToken.builder()
                .user(user)
                .token(newResetCode)
                .purpose(VerificationToken.TokenPurpose.PASSWORD_RESET)
                .expiresAt(Instant.now().plusSeconds(3600)) // 1 hora
                .build();
            
            verificationTokenRepository.save(newToken);
            
            // Enviar email con el nuevo código
            final String userEmail = user.getEmail();
            new Thread(() -> {
                try {
                    emailService.sendPasswordResetEmail(userEmail, newResetCode);
                    log.info("Nuevo código de recuperación enviado a: {}", userEmail);
                } catch (Exception e) {
                    log.error("Error al enviar nuevo código a {}: {}", userEmail, e.getMessage());
                }
            }).start();
            
            // Retornar mensaje especial para código expirado
            return "EXPIRED_NEW_CODE_SENT";
        }
        
        // Validar el código (solo si no está expirado)
        if (!token.getToken().equals(code)) {
            throw new BadRequestException("Código de recuperación incorrecto");
        }
        
        // Verificar que no haya sido usado
        if (token.isConsumed()) {
            throw new BadRequestException("Este código ya ha sido utilizado");
        }
        
        log.info("Código de recuperación verificado exitosamente para: {}", email);
        return "VALID";
    }
    
    /**
     * Restablece la contraseña del usuario con el código de recuperación
     * RN-01: Política de contraseña segura (validada en DTO con @Pattern)
     * RN-04: Validación de código y expiración
     */
    @Transactional
    public void resetPassword(String email, String code, String newPassword, String confirmPassword) {
        log.info("Restableciendo contraseña para: {}", email);
        
        // Validar que las contraseñas coincidan
        if (!newPassword.equals(confirmPassword)) {
            throw new BadRequestException("Las contraseñas no coinciden");
        }
        
        // Buscar usuario
        User user = userRepository.findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));
        
        // Buscar token de recuperación válido (RN-04)
        VerificationToken token = verificationTokenRepository
            .findLatestValidToken(user.getId(), VerificationToken.TokenPurpose.PASSWORD_RESET, Instant.now())
            .orElseThrow(() -> new BadRequestException("Código de recuperación no válido o expirado"));
        
        // Validar el código
        if (!token.getToken().equals(code)) {
            throw new BadRequestException("Código de recuperación incorrecto");
        }
        
        // Verificar expiración (RN-04: 1 hora)
        if (token.isExpired()) {
            throw new BadRequestException("El código de recuperación ha expirado. Solicita uno nuevo.");
        }
        
        // Verificar que no haya sido usado
        if (token.isConsumed()) {
            throw new BadRequestException("Este código ya ha sido utilizado");
        }
        
        // Actualizar contraseña (RN-01 ya validada en DTO)
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        // Marcar token como consumido
        token.setConsumedAt(Instant.now());
        verificationTokenRepository.save(token);
        
        log.info("Contraseña restablecida exitosamente para: {}", email);
    }
    
    /**
     * Inicia sesión para un usuario
     * RN-24: Validación de formato de email (validada en DTO con @Email)
     * RN-02: Cuenta debe estar verificada
     * RN-03: Límite de 3 intentos fallidos con bloqueo de 15 minutos
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public User login(String email, String password) {
        log.info("Intento de inicio de sesión para: {}", email);
        
        // Buscar usuario por email (RN-24 ya validado en DTO)
        User user = userRepository.findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new BadRequestException("Credenciales incorrectas"));
        
        // FA05: Verificar si la cuenta está bloqueada (RN-03)
        if (user.getAccountLockedUntil() != null && user.getAccountLockedUntil().isAfter(Instant.now())) {
            long minutesRemaining = java.time.Duration.between(Instant.now(), user.getAccountLockedUntil()).toMinutes();
            throw new BadRequestException(
                "Tu cuenta está bloqueada temporalmente por seguridad. " +
                "Intenta nuevamente en " + (minutesRemaining + 1) + " minutos."
            );
        }
        
        // Si el bloqueo ya expiró, limpiar el estado
        if (user.getAccountLockedUntil() != null && user.getAccountLockedUntil().isBefore(Instant.now())) {
            user.setAccountLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
        
        // FA03: Verificar si la cuenta está verificada (RN-02)
        if (!user.getEmailConfirmed()) {
            throw new BadRequestException("ACCOUNT_NOT_VERIFIED");
        }
        
        // FA04: Validar contraseña
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            // Incrementar contador de intentos fallidos
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            
            // FA05: Si alcanza 3 intentos, bloquear cuenta por 15 minutos (RN-03)
            if (user.getFailedLoginAttempts() >= 3) {
                user.setAccountLockedUntil(Instant.now().plusSeconds(900)); // 15 minutos
                userRepository.save(user);
                log.warn("Cuenta bloqueada por intentos fallidos: {}", email);
                throw new BadRequestException(
                    "Has alcanzado el límite de intentos. Tu cuenta ha sido bloqueada por 15 minutos por seguridad."
                );
            }
            
            userRepository.save(user);
            log.warn("Intento fallido de inicio de sesión para: {} (intento {} de 3)", email, user.getFailedLoginAttempts());
            throw new BadRequestException("Credenciales incorrectas");
        }
        
        // Login exitoso: Restablecer contador de intentos fallidos (RN-03)
        user.setFailedLoginAttempts(0);
        user.setAccountLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        
        // Actualizar estado de cuenta si todavía está en PENDING_VERIFICATION
        if (user.getAccountStatus() == AccountStatus.PENDING_VERIFICATION) {
            user.setAccountStatus(AccountStatus.ACTIVE);
        }
        
        userRepository.save(user);
        
        log.info("Inicio de sesión exitoso para: {}", email);
        return user;
    }
    
    /**
     * Actualiza el perfil de un usuario (CU05)
     * RN-24: Validación de formato de email
     * RN-25: Unicidad del correo electrónico
     * RN-05: Verificación por cambio de correo
     * RN-01: Usuario debe ser mayor de edad
     */
    @Transactional
    public UpdateProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        log.info("Actualizando perfil para usuario ID: {}", userId);
        
        // Buscar usuario
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));
        
        // RN-01: Validar que el usuario sea mayor de edad (18 años)
        LocalDate today = LocalDate.now();
        int age = Period.between(request.getBirthDate(), today).getYears();
        
        if (age < 18) {
            throw new BadRequestException("Debes ser mayor de edad (18 años o más) para usar esta aplicación");
        }
        
        boolean emailChanged = false;
        String oldEmail = user.getEmail();
        String newEmail = request.getEmail().toLowerCase().trim();
        
        // FA03: Verificar si el email cambió y si ya existe (RN-25)
        if (!oldEmail.equals(newEmail)) {
            Optional<User> existingUser = userRepository.findByEmail(newEmail);
            if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
                throw new BadRequestException("El correo electrónico ya está registrado por otro usuario");
            }
            emailChanged = true;
        }
        
        // Actualizar campos
        user.setDisplayName(request.getDisplayName().trim());
        user.setNickname(request.getNickname() != null ? request.getNickname().trim() : null);
        user.setBirthDate(request.getBirthDate());
        
        // Si cambió el email, marcarlo como no verificado (RN-05)
        if (emailChanged) {
            user.setEmail(newEmail);
            user.setEmailConfirmed(false);
            
            // Crear nuevo token de verificación
            VerificationToken verificationToken = createEmailVerificationToken(user);
            
            // Enviar email de verificación de forma asíncrona
            final String userEmail = user.getEmail();
            final String tokenCode = verificationToken.getToken();
            new Thread(() -> {
                try {
                    emailService.sendVerificationEmail(userEmail, tokenCode);
                    log.info("Email de verificación enviado a: {}", userEmail);
                } catch (Exception e) {
                    log.error("Error al enviar email de verificación a {}: {}", userEmail, e.getMessage());
                }
            }).start();
            
            log.info("Email cambiado de {} a {}. Verificación pendiente.", oldEmail, newEmail);
        }
        
        // Guardar cambios
        user = userRepository.save(user);
        
        log.info("Perfil actualizado exitosamente para usuario ID: {}", userId);
        
        return UpdateProfileResponse.builder()
            .userId(user.getId())
            .email(user.getEmail())
            .displayName(user.getDisplayName())
            .nickname(user.getNickname())
            .emailConfirmed(user.getEmailConfirmed())
            .emailChanged(emailChanged)
            .message(emailChanged ? 
                "Perfil actualizado. Se ha enviado un código de verificación a tu nuevo correo." : 
                "Perfil actualizado correctamente")
            .updatedAt(user.getUpdatedAt())
            .build();
    }
    
    /**
     * Actualizar avatar del usuario
     * Máximo 1 MB para MEDIUMBLOB
     */
    @Transactional
    public UpdateAvatarResponse updateAvatar(Long userId, UpdateAvatarRequest request) {
        log.info("Actualizando avatar para usuario ID: {}", userId);
        
        // Buscar usuario
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));
        
        // Parsear la imagen Base64
        String base64Data = request.getImageBase64();
        
        // Extraer MIME type y datos
        String[] parts = base64Data.split(",");
        if (parts.length != 2) {
            throw new BadRequestException("Formato de imagen inválido");
        }
        
        String mimeType = parts[0].split(":")[1].split(";")[0];
        String base64Image = parts[1];
        
        // Decodificar Base64
        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(base64Image);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Error al decodificar la imagen");
        }
        
        // Validar tamaño (máximo 1 MB para MEDIUMBLOB)
        if (imageBytes.length > 1024 * 1024) {
            throw new BadRequestException("La imagen es demasiado grande. Máximo 1 MB permitido.");
        }
        
        // Buscar o crear UserProfile
        UserProfile profile = userProfileRepository.findById(userId).orElse(null);
        
        if (profile == null) {
            // Crear nuevo perfil usando SQL nativo para evitar problemas con Hibernate
            entityManager.createNativeQuery(
                "INSERT INTO user_profile (user_id, avatar_bytes, avatar_mime, updated_at) VALUES (?, ?, ?, ?)"
            )
            .setParameter(1, userId)
            .setParameter(2, imageBytes)
            .setParameter(3, mimeType)
            .setParameter(4, Instant.now())
            .executeUpdate();
            
            entityManager.flush();
            log.info("Avatar creado exitosamente para usuario ID: {}", userId);
        } else {
            // Actualizar avatar existente
            profile.setAvatarBytes(imageBytes);
            profile.setAvatarMime(mimeType);
            userProfileRepository.save(profile);
            log.info("Avatar actualizado exitosamente para usuario ID: {}", userId);
        }
        
        return UpdateAvatarResponse.builder()
            .success(true)
            .message("Avatar actualizado correctamente")
            .avatarUrl("/api/profile/" + userId + "/avatar")
            .build();
    }
    
    /**
     * Obtener avatar del usuario
     */
    @Transactional(readOnly = true)
    public AvatarData getAvatar(Long userId) {
        log.info("Obteniendo avatar para usuario ID: {}", userId);
        
        Optional<UserProfile> profileOpt = userProfileRepository.findById(userId);
        
        if (profileOpt.isEmpty() || profileOpt.get().getAvatarBytes() == null) {
            log.info("No hay avatar para usuario ID: {}", userId);
            return null;
        }
        
        UserProfile profile = profileOpt.get();
        
        return AvatarData.builder()
            .bytes(profile.getAvatarBytes())
            .mimeType(profile.getAvatarMime())
            .build();
    }
    
    /**
     * Eliminar avatar del usuario
     */
    @Transactional
    public UpdateAvatarResponse deleteAvatar(Long userId) {
        log.info("Eliminando avatar para usuario ID: {}", userId);
        
        // Buscar usuario
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));
        
        // Buscar perfil
        UserProfile profile = userProfileRepository.findById(userId).orElse(null);
        
        if (profile == null || profile.getAvatarBytes() == null) {
            log.info("No hay avatar para eliminar para usuario ID: {}", userId);
            return UpdateAvatarResponse.builder()
                .success(true)
                .message("No hay avatar para eliminar")
                .avatarUrl(null)
                .build();
        }
        
        // Eliminar avatar
        profile.setAvatarBytes(null);
        profile.setAvatarMime(null);
        userProfileRepository.save(profile);
        
        log.info("Avatar eliminado exitosamente para usuario ID: {}", userId);
        
        return UpdateAvatarResponse.builder()
            .success(true)
            .message("Avatar eliminado correctamente")
            .avatarUrl(null)
            .build();
    }
    
    /**
     * CU07 - Eliminar cuenta de usuario
     * RN-06: Doble confirmación (validación de contraseña)
     * RN-07: Eliminación irreversible de todos los datos
     */
    @Transactional
    public DeleteAccountResponse deleteAccount(Long userId, DeleteAccountRequest request) {
        log.info("Iniciando eliminación de cuenta para usuario ID: {}", userId);
        
        // Obtener usuario
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));
        
        // Validar contraseña (segunda confirmación - RN-06)
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Intento de eliminación con contraseña incorrecta para usuario ID: {}", userId);
            throw new BadRequestException("La contraseña ingresada es incorrecta");
        }
        
        String deletedEmail = user.getEmail();
        
        try {
            // RN-07: Eliminar todos los datos asociados de forma irreversible
            
            // 1. Eliminar vínculos activos donde el usuario participa
            linkRepository.deleteByInitiatorUserIdOrPartnerUserId(userId, userId);
            log.info("Vínculos eliminados para usuario ID: {}", userId);
            
            // 2. Eliminar códigos de vínculo generados por el usuario
            linkCodeRepository.deleteByGeneratedByUserId(userId);
            log.info("Códigos de vínculo eliminados para usuario ID: {}", userId);
            
            // 3. Eliminar perfil (incluye avatar)
            userProfileRepository.deleteById(userId);
            log.info("Perfil eliminado para usuario ID: {}", userId);
            
            // 4. Eliminar todos los tokens (verificación y reset de contraseña)
            verificationTokenRepository.deleteByUserId(userId);
            log.info("Tokens eliminados para usuario ID: {}", userId);
            
            // 5. Eliminar usuario (última operación)
            userRepository.delete(user);
            log.info("Usuario eliminado definitivamente ID: {}", userId);
            
            entityManager.flush();
            
            log.info("Cuenta eliminada exitosamente: {}", deletedEmail);
            
            return DeleteAccountResponse.builder()
                .message("Tu cuenta ha sido eliminada permanentemente")
                .deletedEmail(deletedEmail)
                .build();
                
        } catch (Exception e) {
            // FA04 - Error del sistema al eliminar la cuenta
            log.error("Error al eliminar cuenta para usuario ID {}: {}", userId, e.getMessage(), e);
            throw new BadRequestException("No se pudo eliminar la cuenta. Por favor, intenta nuevamente más tarde.");
        }
    }
    
    /**
     * Actualiza el token FCM del usuario para notificaciones push
     * @param userId ID del usuario
     * @param fcmToken Token FCM del dispositivo
     */
    @Transactional
    public void updateFCMToken(Long userId, String fcmToken) {
        log.info("Actualizando token FCM para usuario ID: {}", userId);
        
        // Buscar usuario
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));
        
        // Actualizar token
        user.setFcmToken(fcmToken);
        userRepository.save(user);
        
        log.info("Token FCM actualizado exitosamente para usuario ID: {}", userId);
    }
}
