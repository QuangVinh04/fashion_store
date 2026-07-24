package com.fashionstore.identity.repository;

import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, String> {
    Optional<VerificationToken> findByToken(String token);
    Optional<VerificationToken> findByUserAndUsedFalse(User user);
    void deleteByUserId(String userId);

    Optional<VerificationToken> findByUserId(String userId);
}
