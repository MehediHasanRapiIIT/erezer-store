package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.NewsletterSubscriber;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NewsletterSubscriberRepository extends JpaRepository<NewsletterSubscriber, UUID> {

    @Query("SELECT s FROM NewsletterSubscriber s " +
            "WHERE LOWER(s.email) = LOWER(:email) AND s.deleted = false")
    Optional<NewsletterSubscriber> findByEmailIgnoreCase(@Param("email") String email);

    Optional<NewsletterSubscriber> findByUnsubscribeToken(String token);

    @Query("SELECT s FROM NewsletterSubscriber s WHERE s.deleted = false AND s.status = 'SUBSCRIBED' " +
            "ORDER BY s.subscribedAt DESC")
    List<NewsletterSubscriber> findAllSubscribed();

    /** The admin subscriber list, newest first; {@code q} is a lower-case "%text%" pattern (or null) on the email. */
    String ADMIN_FILTERS = "AND (:status IS NULL OR s.status = :status) " +
            "AND (:q IS NULL OR LOWER(s.email) LIKE :q ESCAPE '\\') ";

    @Query(value = "SELECT s FROM NewsletterSubscriber s WHERE s.deleted = false " + ADMIN_FILTERS +
            "ORDER BY s.subscribedAt DESC, s.id",
            countQuery = "SELECT COUNT(s) FROM NewsletterSubscriber s WHERE s.deleted = false " + ADMIN_FILTERS)
    Page<NewsletterSubscriber> findForAdmin(@Param("status") String status, @Param("q") String q, Pageable pageable);

    long countByStatusAndDeletedFalse(String status);
}
