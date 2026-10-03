package com.fashionstore.identity.entity;



import com.fashionstore.common.persistence.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User extends AuditedEntity {
    @Column(unique = true, nullable = false)
    String email;

    @Column(nullable = false)
    String fullName;

    String phone;
    String address;
    @Column(length = 1000)
    String avatar;

    @Column(name = "avatar_media_id", length = 36)
    String avatarMediaId;

    @Builder.Default
    @Column(name = "avatar_revision", nullable = false)
    Long avatarRevision = 0L;

    @Builder.Default
    @Column(nullable = false)
    Boolean isActive = true;

    @Builder.Default
    @Column(nullable = false)
    Boolean isEmailVerified = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    Set<Role> roles = new HashSet<>();

}
