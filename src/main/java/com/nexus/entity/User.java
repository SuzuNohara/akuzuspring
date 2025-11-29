package com.nexus.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import com.nexus.model.AccountStatus;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(name = "email_confirmed", nullable = false)
    private Boolean emailConfirmed = false;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @Column(name = "display_name", nullable = false)
    private String displayName;
    
    @Column(name = "nickname")
    private String nickname;
    
    @Column(name = "link_code", nullable = false, unique = true, length = 32)
    private String linkCode;
    
    @Column(name = "link_code_version", nullable = false)
    private Integer linkCodeVersion = 1;
    
    @Column(name = "link_code_updated_at", nullable = false)
    private Instant linkCodeUpdatedAt;
    
    @Column(name = "fcm_token", length = 500)
    private String fcmToken;
    
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    
    // RN-03: Control de intentos fallidos de inicio de sesión
    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;
    
    @Column(name = "account_locked_until")
    private Instant accountLockedUntil;
    
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "terms_accepted", nullable = false)
    private Boolean termsAccepted = false;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 30)
    private AccountStatus accountStatus = AccountStatus.PENDING_VERIFICATION;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserProfile profile;
    
    @PrePersist
    protected void onCreate() {
        if (linkCodeUpdatedAt == null) {
            linkCodeUpdatedAt = Instant.now();
        }
        if (accountStatus == null) {
            accountStatus = AccountStatus.PENDING_VERIFICATION;
        }
        if (Boolean.TRUE.equals(termsAccepted) && termsAcceptedAt == null) {
            termsAcceptedAt = Instant.now();
        }
        if (failedLoginAttempts == null) {
            failedLoginAttempts = 0;
        }
    }
}
