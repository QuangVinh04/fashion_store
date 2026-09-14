package com.fashionstore.identity.repository;

import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.entity.UserAddress;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, String> {

    List<UserAddress> findByUserIdOrderByIsDefaultDescCreatedAtDesc(String userId);

    Optional<UserAddress> findByIdAndUserId(String id, String userId);

    boolean existsByUserId(String userId);

    Optional<UserAddress> findFirstByUserIdAndIdNotOrderByCreatedAtAsc(String userId, String id);

    @Modifying
    @Query("UPDATE UserAddress a SET a.isDefault = false WHERE a.userId = :userId")
    void resetDefaultAddress(@Param("userId") String userId);

    @Modifying
    @Query("UPDATE UserAddress a SET a.isDefault = false WHERE a.userId = :userId AND a.id != :addressId")
    void resetOtherDefaultAddresses(@Param("userId") String userId, @Param("addressId") String addressId);
}
