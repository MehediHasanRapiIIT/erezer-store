package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.ShippingZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShippingZoneRepository extends JpaRepository<ShippingZone, Long> {

    @Query("SELECT z FROM ShippingZone z WHERE z.deleted = false AND z.isActive = true " +
            "ORDER BY z.sortOrder ASC, z.id ASC")
    List<ShippingZone> findAllActive();

    @Query("SELECT z FROM ShippingZone z WHERE z.deleted = false ORDER BY z.sortOrder ASC, z.id ASC")
    List<ShippingZone> findAllForAdmin();

    @Query("SELECT z FROM ShippingZone z WHERE z.deleted = false AND z.isDefault = true")
    Optional<ShippingZone> findDefault();
}
