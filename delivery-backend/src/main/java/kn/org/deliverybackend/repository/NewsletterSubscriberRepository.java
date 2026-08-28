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

    @Query(value = "SELECT s FROM NewsletterSubscriber s WHERE s.deleted = false " +
            "AND (:status IS NULL OR s.status = :status) " +
            "ORDER BY s.subscribedAt DESC",
            countQuery = "SELECT COUNT(s) FROM NewsletterSubscriber s WHERE s.deleted = false " +
                    "AND (:status IS NULL OR s.status = :status)")
    Page<NewsletterSubscriber> findForAdmin(@Param("status") String status, Pageable pageable);

    long countByStatusAndDeletedFalse(String status);
}
