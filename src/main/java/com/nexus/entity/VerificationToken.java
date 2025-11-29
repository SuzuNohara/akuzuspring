package com.nexus.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.stream.Stream;

@Entity
@Table(name = "verification_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class VerificationToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false, unique = true)
    private String token;
    
    @Convert(converter = TokenPurposeConverter.class)
    @Column(nullable = false, length = 20)
    private TokenPurpose purpose;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    @Column(name = "consumed_at")
    private Instant consumedAt;
    
    public enum TokenPurpose {
        EMAIL_CONFIRM("email_confirm"),
        PASSWORD_RESET("password_reset");
        
        private final String value;
        
        TokenPurpose(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static TokenPurpose fromValue(String value) {
            return Stream.of(TokenPurpose.values())
                .filter(purpose -> purpose.getValue().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown value: " + value));
        }
    }
    
    @Converter(autoApply = true)
    public static class TokenPurposeConverter implements AttributeConverter<TokenPurpose, String> {
        
        @Override
        public String convertToDatabaseColumn(TokenPurpose purpose) {
            if (purpose == null) {
                return null;
            }
            return purpose.getValue();
        }
        
        @Override
        public TokenPurpose convertToEntityAttribute(String value) {
            if (value == null) {
                return null;
            }
            return TokenPurpose.fromValue(value);
        }
    }
    
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    public boolean isConsumed() {
        return consumedAt != null;
    }
}
