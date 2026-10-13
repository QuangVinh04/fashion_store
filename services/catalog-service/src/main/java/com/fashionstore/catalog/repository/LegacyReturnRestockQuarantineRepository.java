package com.fashionstore.catalog.repository;
import com.fashionstore.catalog.entity.LegacyReturnRestockQuarantine;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LegacyReturnRestockQuarantineRepository extends JpaRepository<LegacyReturnRestockQuarantine,String> {
    java.util.Optional<LegacyReturnRestockQuarantine> findByMessageId(String id);
    org.springframework.data.domain.Page<LegacyReturnRestockQuarantine> findByStatus(String status,org.springframework.data.domain.Pageable pageable);
}
