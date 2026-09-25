package com.fashionstore.identity.repository;

import com.fashionstore.identity.entity.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // id = Keycloak sub (không để Hibernate sinh UUID) — JPA save() không nhận id gán sẵn với @GeneratedValue
    @Modifying
    @Query(value = """
            insert into users (id, email, full_name, is_active, is_email_verified, created_at, updated_at)
            values (:id, :email, :fullName, true, :emailVerified, now(), now())
            """, nativeQuery = true)
    void insertProvisionedUser(@Param("id") String id,
                               @Param("email") String email,
                               @Param("fullName") String fullName,
                               @Param("emailVerified") boolean emailVerified);
}
