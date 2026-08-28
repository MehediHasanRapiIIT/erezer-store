package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.NewsletterCampaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NewsletterCampaignRepository extends JpaRepository<NewsletterCampaign, UUID> {

    @Query(value = "SELECT c FROM NewsletterCampaign c WHERE c.deleted = false ORDER BY c.createdAt DESC",
            countQuery = "SELECT COUNT(c) FROM NewsletterCampaign c WHERE c.deleted = false")
    Page<NewsletterCampaign> findAllForAdmin(Pageable pageable);
}
