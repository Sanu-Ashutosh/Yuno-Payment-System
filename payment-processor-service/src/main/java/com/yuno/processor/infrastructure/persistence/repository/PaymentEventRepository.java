package com.yuno.processor.infrastructure.persistence.repository;

import com.yuno.processor.infrastructure.persistence.entity.PaymentEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEventEntity, UUID> {
    List<PaymentEventEntity> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);
}
