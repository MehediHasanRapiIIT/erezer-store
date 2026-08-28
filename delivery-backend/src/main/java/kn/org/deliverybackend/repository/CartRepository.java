package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {

    @Query("SELECT c FROM Cart c WHERE c.userId = :userId")
    List<Cart> findByUserId(UUID userId);

    @Query("SELECT c FROM Cart c WHERE c.id = :id AND c.userId = :userId")
    Optional<Cart> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT c FROM Cart c WHERE c.userId = :userId AND c.productId = :productId")
    Optional<Cart> findByUserIdAndProductId(UUID userId, Long productId);

    // Variant-aware lookup: a null variantId matches the base-product line (also
    // null), so each size/colour variant is tracked as its own cart line.
    @Query("SELECT c FROM Cart c WHERE c.userId = :userId AND c.productId = :productId " +
            "AND ((:variantId IS NULL AND c.variantId IS NULL) OR c.variantId = :variantId)")
    Optional<Cart> findByUserIdAndProductIdAndVariantId(UUID userId, Long productId, Long variantId);

    @Query("SELECT COUNT(c) FROM Cart c WHERE c.userId = :userId")
    long countByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    /**
     * Distinct user-ids whose cart has been idle for at least the supplied
     * cut-off AND who haven't been emailed about *this* cart state yet
     * (lastEmailedAt is null or older than the most recent cart update).
     *
     * Use as the seed for {@link #findByUserId} per user — keeps the scan
     * memory-bounded.
     */
    @Query("SELECT DISTINCT c.userId FROM Cart c " +
            "WHERE c.userId IS NOT NULL " +
            "  AND c.updatedAt <= :idleBefore " +
            "  AND (c.lastEmailedAt IS NULL OR c.lastEmailedAt < c.updatedAt) " +
            "  AND c.deleted = false")
    List<UUID> findUsersWithStaleCart(@org.springframework.data.repository.query.Param("idleBefore")
                                       java.util.Date idleBefore);
}