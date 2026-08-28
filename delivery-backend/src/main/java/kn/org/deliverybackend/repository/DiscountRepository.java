package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Discount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, UUID> {

    @Query("SELECT d FROM Discount d WHERE d.isActive = true AND d.deleted = false")
    List<Discount> findActive();
}
