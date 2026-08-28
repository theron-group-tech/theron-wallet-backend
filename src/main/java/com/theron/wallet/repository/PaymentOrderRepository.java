package com.theron.wallet.repository;

import com.theron.wallet.entity.PaymentOrder;
import com.theron.wallet.enums.PaymentOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    Page<PaymentOrder> findByOrganization_IdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<PaymentOrder> findByOrganization_IdAndStatusOrderByCreatedAtDesc(
            UUID organizationId, PaymentOrderStatus status, Pageable pageable);

    /** Compatibility alias used by the payment-order service. */
    default Page<PaymentOrder> findByOrganization_Id(UUID organizationId, Pageable pageable) {
        return findByOrganization_IdOrderByCreatedAtDesc(organizationId, pageable);
    }

    /** Compatibility alias used by the payment-order service. */
    default Page<PaymentOrder> findByOrganization_IdAndStatus(
            UUID organizationId, PaymentOrderStatus status, Pageable pageable) {
        return findByOrganization_IdAndStatusOrderByCreatedAtDesc(organizationId, status, pageable);
    }

    @Query("""
            SELECT po FROM PaymentOrder po
            JOIN FETCH po.organization
            LEFT JOIN FETCH po.sourceAccount
            JOIN FETCH po.destinationAccount
            JOIN FETCH po.createdBy
            WHERE po.id = :id
            """)
    Optional<PaymentOrder> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
            SELECT po FROM PaymentOrder po
            JOIN FETCH po.organization
            LEFT JOIN FETCH po.sourceAccount
            JOIN FETCH po.destinationAccount
            WHERE po.debitTransaction.id = :debitTransactionId
            """)
    Optional<PaymentOrder> findByDebitTransactionId(@Param("debitTransactionId") UUID debitTransactionId);
}
