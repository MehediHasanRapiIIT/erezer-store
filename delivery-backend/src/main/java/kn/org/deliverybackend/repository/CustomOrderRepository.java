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

    boolean existsByReference(String reference);

    @Query(value = "SELECT o FROM CustomOrder o WHERE o.deleted = false " +
            "AND (:status IS NULL OR o.status = :status) ORDER BY o.createdAt DESC",
            countQuery = "SELECT COUNT(o) FROM CustomOrder o WHERE o.deleted = false " +
                    "AND (:status IS NULL OR o.status = :status)")
    Page<CustomOrder> findForAdmin(@Param("status") CustomOrderStatus status, Pageable pageable);

    /** Active list: everything whose status is not the given one (used to hide DELIVERED). */
    @Query(value = "SELECT o FROM CustomOrder o WHERE o.deleted = false " +
            "AND o.status <> :status ORDER BY o.createdAt DESC",
            countQuery = "SELECT COUNT(o) FROM CustomOrder o WHERE o.deleted = false " +
                    "AND o.status <> :status")
    Page<CustomOrder> findForAdminExcluding(@Param("status") CustomOrderStatus status, Pageable pageable);
}
