package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.FlashSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FlashSaleRepository extends JpaRepository<FlashSale, UUID> {

    /** Active, non-deleted campaigns; window filtering is applied in the service. */
    @Query("SELECT f FROM FlashSale f WHERE f.isActive = true AND f.deleted = false")
    List<FlashSale> findActive();
}
