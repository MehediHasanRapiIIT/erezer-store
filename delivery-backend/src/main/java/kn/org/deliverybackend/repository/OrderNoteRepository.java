package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.OrderNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderNoteRepository extends JpaRepository<OrderNote, UUID> {

    @Query("SELECT n FROM OrderNote n WHERE n.orderId = :orderId AND n.deleted = false " +
            "ORDER BY n.createdAt DESC")
    List<OrderNote> findByOrderId(@Param("orderId") UUID orderId);
}
