package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.ContactMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {

    @Query(value = "SELECT m FROM ContactMessage m WHERE m.deleted = false " +
            "AND (:status IS NULL OR m.status = :status) ORDER BY m.createdAt DESC",
            countQuery = "SELECT COUNT(m) FROM ContactMessage m WHERE m.deleted = false " +
                    "AND (:status IS NULL OR m.status = :status)")
    Page<ContactMessage> findForAdmin(@Param("status") String status, Pageable pageable);
}
