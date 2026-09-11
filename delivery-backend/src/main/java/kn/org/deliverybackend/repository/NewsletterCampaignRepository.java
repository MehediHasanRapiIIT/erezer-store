package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.NewsletterCampaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NewsletterCampaignRepository extends JpaRepository<NewsletterCampaign, UUID> {

    /** The admin campaign list, newest first; {@code q} is a lower-case "%text%" pattern (or null) on the subject. */
    @Query(value = "SELECT c FROM NewsletterCampaign c WHERE c.deleted = false " +
            "AND (:q IS NULL OR LOWER(c.subject) LIKE :q ESCAPE '\\') ORDER BY c.createdAt DESC, c.id",
            countQuery = "SELECT COUNT(c) FROM NewsletterCampaign c WHERE c.deleted = false " +
                    "AND (:q IS NULL OR LOWER(c.subject) LIKE :q ESCAPE '\\')")
    Page<NewsletterCampaign> findAllForAdmin(@Param("q") String q, Pageable pageable);
}
