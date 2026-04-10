package com.fashionstore.clothes_retail_api.modules.auth.entity;


import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;


@Getter
@Setter
@Builder
@Entity
@Table(name = "permissions")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    // Ví dụ: "product:write", "order:manage", "user:read"
    @Column(name = "name", unique = true, nullable = false)
    String name;

    @Column(name = "description")
    String description;
}
