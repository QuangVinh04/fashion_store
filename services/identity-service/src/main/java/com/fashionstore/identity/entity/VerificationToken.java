package com.fashionstore.identity.entity;

import com.fashionstore.common.persistence.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "verification_tokens")
public class VerificationToken extends AuditedEntity {
    @Column(nullable = false, unique = true)
    String token;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(nullable = false)
    LocalDateTime expiresAt;

    @Builder.Default
    @Column(nullable = false)
    Boolean used = false;
}
