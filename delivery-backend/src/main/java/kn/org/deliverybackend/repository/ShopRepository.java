package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {

    @Query("SELECT s FROM Shop s WHERE s.deleted = false AND (" +
            "LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(s.location) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY s.name ASC")
    Page<Shop> search(@Param("q") String q, Pageable pageable);
}
