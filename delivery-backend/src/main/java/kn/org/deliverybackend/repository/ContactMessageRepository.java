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

    /**
     * The admin support inbox, newest first. {@code q} is a lower-case "%text%"
     * pattern (or null) matched against the name, email, subject and message.
     */
    String ADMIN_FILTERS = "AND (:status IS NULL OR m.status = :status) " +
            "AND (:q IS NULL OR LOWER(m.name) LIKE :q ESCAPE '\\' OR LOWER(m.email) LIKE :q ESCAPE '\\' " +
            "  OR LOWER(m.subject) LIKE :q ESCAPE '\\' OR LOWER(m.message) LIKE :q ESCAPE '\\') ";

    @Query(value = "SELECT m FROM ContactMessage m WHERE m.deleted = false " + ADMIN_FILTERS +
            "ORDER BY m.createdAt DESC, m.id",
            countQuery = "SELECT COUNT(m) FROM ContactMessage m WHERE m.deleted = false " + ADMIN_FILTERS)
    Page<ContactMessage> findForAdmin(@Param("status") String status, @Param("q") String q, Pageable pageable);
}
