package com.fashionstore.clothes_retail_api.config.web;


import com.fashionstore.clothes_retail_api.modules.auth.constant.PredefinedRole;
import com.fashionstore.clothes_retail_api.modules.auth.entity.Permission;
import com.fashionstore.clothes_retail_api.modules.auth.entity.Role;
import com.fashionstore.clothes_retail_api.modules.auth.entity.User;
import com.fashionstore.clothes_retail_api.modules.auth.repository.PermissionRepository;
import com.fashionstore.clothes_retail_api.modules.auth.repository.RoleRepository;
import com.fashionstore.clothes_retail_api.modules.auth.repository.UserRepository;
import com.fashionstore.clothes_retail_api.modules.category.entity.Category;
import com.fashionstore.clothes_retail_api.modules.category.repository.CategoryRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ApplicationInitConfig  {

    PasswordEncoder passwordEncoder;

    @NonFinal
    static final String ADMIN_EMAIL    = "admin@fashionstore.com";
    @NonFinal
    static final String ADMIN_PASSWORD = "Admin@123";

    @Bean
    @ConditionalOnProperty(
            prefix = "spring",
            value = "datasource.driver-class-name",
            havingValue = "org.postgresql.Driver",
            matchIfMissing = true
    )
    @Transactional
    ApplicationRunner applicationRunner(UserRepository userRepository,
                                        RoleRepository roleRepository,
                                        PermissionRepository permissionRepository,
                                        CategoryRepository categoryRepository) {
        log.info("Initializing application.....");
        return args -> {
            // 1. Seed Permissions
            seedPermissions(permissionRepository);
            // 2. Seed Roles
            Optional<Role> userRole = roleRepository.findByName(PredefinedRole.USER_ROLE);
            if (userRole.isEmpty()) {
                Set<Permission> userPerms = new HashSet<>(permissionRepository.findAllByNameIn(
                        List.of("product:read", "category:read", "order:read")
                ));
                roleRepository.save(Role.builder()
                        .name(PredefinedRole.USER_ROLE)
                        .description("Khách hàng thông thường")
                        .permissions(userPerms)
                        .build());
                log.info("Seeded role: {}", PredefinedRole.USER_ROLE);
            }
            Optional<Role> adminRole = roleRepository.findByName(PredefinedRole.ADMIN_ROLE);
            if (adminRole.isEmpty()) {
                Set<Permission> adminPerms = new HashSet<>(permissionRepository.findAll());
                roleRepository.save(Role.builder()
                        .name(PredefinedRole.ADMIN_ROLE)
                        .description("Quản trị viên toàn quyền")
                        .permissions(adminPerms)
                        .build());
                log.info("Seeded role: {}", PredefinedRole.ADMIN_ROLE);
            }
            // 3. Seed Admin User
            if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
                Role role = roleRepository.findByName(PredefinedRole.ADMIN_ROLE)
                        .orElseThrow(() -> new RuntimeException("Admin role not found"));
                var roles = new HashSet<Role>();
                roles.add(role);
                User adminUser = User.builder()
                        .email(ADMIN_EMAIL)
                        .password(passwordEncoder.encode(ADMIN_PASSWORD))
                        .fullName("Administrator")
                        .isActive(true)
                        .isEmailVerified(true)
                        .roles(roles)
                        .build();
                userRepository.save(adminUser);
                log.warn("Admin user has been created with default password: Admin@123, please change it");
            }
            //  4. Seed Default Category
            if (categoryRepository.count() == 0) {
                categoryRepository.save(Category.builder()
                        .name("Tất cả sản phẩm")
                        .slug("tat-ca-san-pham")
                        .description("Danh mục mặc định")
                        .build());
                log.info("Seeded default category");
            }
            log.info("Application initialization completed .....");
        };
    }
    private void seedPermissions(PermissionRepository permissionRepository) {
        List<String[]> permissions = List.of(
                new String[]{"product:read",    "Xem sản phẩm"},
                new String[]{"product:write",   "Tạo và sửa sản phẩm"},
                new String[]{"product:delete",  "Xóa sản phẩm"},
                new String[]{"category:read",   "Xem danh mục"},
                new String[]{"category:write",  "Tạo và sửa danh mục"},
                new String[]{"category:delete", "Xóa danh mục"},
                new String[]{"order:read",      "Xem đơn hàng"},
                new String[]{"order:manage",    "Quản lý đơn hàng"},
                new String[]{"user:read",       "Xem người dùng"},
                new String[]{"user:manage",     "Quản lý người dùng"}
        );
        permissions.forEach(p -> {
            if (!permissionRepository.existsByName(p[0])) {
                permissionRepository.save(Permission.builder()
                        .name(p[0])
                        .description(p[1])
                        .build());
                log.info("Seeded permission: {}", p[0]);
            }
        });
    }

}
