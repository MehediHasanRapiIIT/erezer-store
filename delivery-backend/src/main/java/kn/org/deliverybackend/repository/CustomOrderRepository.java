package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.CustomOrder;
import kn.org.deliverybackend.enumeration.CustomOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CustomOrderRepository extends JpaRepository<CustomOrder, UUID> {

    /** {@code q} is a lower-case "%text%" pattern (or null): reference, customer name, phone, email, item. */
    String ADMIN_SEARCH = "AND (:q IS NULL OR LOWER(o.reference) LIKE :q ESCAPE '\\' " +
            "OR LOWER(CONCAT(COALESCE(o.firstName, ''), ' ', COALESCE(o.lastName, ''))) LIKE :q ESCAPE '\\' " +
            "OR o.phone LIKE :q ESCAPE '\\' OR LOWER(o.email) LIKE :q ESCAPE '\\' " +
            "OR LOWER(o.itemName) LIKE :q ESCAPE '\\') ";

    boolean existsByReference(String reference);

    // The id breaks ties, so paging never repeats or skips a request.
    @Query(value = "SELECT o FROM CustomOrder o WHERE o.deleted = false " +
            "AND (:status IS NULL OR o.status = :status) " + ADMIN_SEARCH + "ORDER BY o.createdAt DESC, o.id",
            countQuery = "SELECT COUNT(o) FROM CustomOrder o WHERE o.deleted = false " +
                    "AND (:status IS NULL OR o.status = :status) " + ADMIN_SEARCH)
    Page<CustomOrder> findForAdmin(@Param("status") CustomOrderStatus status, @Param("q") String q,
                                   Pageable pageable);

    /** Active list: everything whose status is not the given one (used to hide DELIVERED). */
    @Query(value = "SELECT o FROM CustomOrder o WHERE o.deleted = false " +
            "AND o.status <> :status " + ADMIN_SEARCH + "ORDER BY o.createdAt DESC, o.id",
            countQuery = "SELECT COUNT(o) FROM CustomOrder o WHERE o.deleted = false " +
                    "AND o.status <> :status " + ADMIN_SEARCH)
    Page<CustomOrder> findForAdminExcluding(@Param("status") CustomOrderStatus status, @Param("q") String q,
                                            Pageable pageable);
}
