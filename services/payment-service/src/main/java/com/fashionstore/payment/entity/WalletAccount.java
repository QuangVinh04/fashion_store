package com.fashionstore.payment.entity;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "wallet_account")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletAccount extends BaseEntity {
    @Column(name = "user_id", nullable = false, unique = true) private String userId;
    @Column(nullable = false, length = 3) @Builder.Default private String currency = "VND";
    @Column(nullable = false) private long balance;
    @Version @Column(nullable = false) private long version;
}
