package com.fashionstore.clothes_retail_api.modules.auth.repository;

import com.fashionstore.clothes_retail_api.modules.auth.entity.User;
import com.fashionstore.clothes_retail_api.modules.auth.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, String> {
    Optional<VerificationToken> findByToken(String token);
    Optional<VerificationToken> findByUserAndUsedFalse(User user);
    void deleteByUserId(String userId);

    Optional<VerificationToken> findByUserId(String userId);
}
