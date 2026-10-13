package com.fashionstore.payment.repository;

import com.fashionstore.payment.entity.WalletAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, String> {
    Optional<WalletAccount> findByUserId(String userId);
    org.springframework.data.domain.Page<WalletAccount> findByUserId(String userId,org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletAccount w where w.userId = :userId")
    Optional<WalletAccount> findByUserIdForUpdate(@Param("userId") String userId);

    @Modifying
    @Query(value = """
            insert into wallet_account (id,user_id,currency,balance,version,created_at,updated_at)
            values (:id,:userId,'VND',0,0,current_timestamp,current_timestamp) on conflict do nothing
            """, nativeQuery = true)
    int createIfAbsent(@Param("id") String id, @Param("userId") String userId);
}
