package com.fashionstore.identity.repository;

import com.fashionstore.identity.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") String id);

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
