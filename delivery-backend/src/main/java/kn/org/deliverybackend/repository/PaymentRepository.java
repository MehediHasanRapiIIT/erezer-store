package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);

    Optional<Payment> findFirstByOrderIdAndProviderOrderByCreatedAtDesc(UUID orderId, String provider);
}
