package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {

    List<PaymentRefund> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    List<PaymentRefund> findByPaymentId(UUID paymentId);
}
