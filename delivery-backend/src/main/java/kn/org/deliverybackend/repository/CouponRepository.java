package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    @Query("SELECT c FROM Coupon c WHERE LOWER(c.code) = LOWER(:code) AND c.deleted = false")
    Optional<Coupon> findByCodeIgnoreCase(@Param("code") String code);
}
