package com.yuno.processor.infrastructure.persistence.repository;

import com.yuno.common.enums.PaymentMethod;
import com.yuno.common.enums.PaymentStatus;
import com.yuno.common.enums.ProviderType;
import com.yuno.processor.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COUNT(p) FROM PaymentEntity p WHERE p.status = :status")
    long countByStatus(PaymentStatus status);

    @Query("SELECT COUNT(p) FROM PaymentEntity p WHERE p.paymentMethod = :method")
    long countByPaymentMethod(PaymentMethod method);

    @Query("SELECT COUNT(p) FROM PaymentEntity p WHERE p.provider = :provider")
    long countByProvider(ProviderType provider);

    @Query("SELECT AVG(p.retryCount) FROM PaymentEntity p")
    Double avgRetryCount();
}
