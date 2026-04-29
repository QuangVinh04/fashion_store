package com.fashionstore.clothes_retail_api.modules.auth.entity;

import com.fashionstore.clothes_retail_api.common.entity.BaseEntity;
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
public class VerificationToken extends BaseEntity {
    @Column(nullable = false, unique = true)
    String token;  // UUID random

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(nullable = false)
    LocalDateTime expiresAt;

    Boolean used = false;
}
