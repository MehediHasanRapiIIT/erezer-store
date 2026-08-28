package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    @Query("SELECT oi FROM OrderItem oi WHERE oi.orderId = :orderId AND oi.deleted = false")
    List<OrderItem> findByOrderId(UUID orderId);
}
