package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.BundleOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BundleOfferRepository extends JpaRepository<BundleOffer, UUID> {

    @Query("SELECT b FROM BundleOffer b WHERE b.deleted = false AND b.isActive = true " +
            "ORDER BY b.sortOrder ASC, b.createdAt DESC")
    List<BundleOffer> findActive();

    @Query("SELECT b FROM BundleOffer b WHERE b.deleted = false " +
            "ORDER BY b.sortOrder ASC, b.createdAt DESC")
    List<BundleOffer> findAllForAdmin();
}
