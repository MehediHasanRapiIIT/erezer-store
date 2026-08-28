package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.NewsletterSendLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NewsletterSendLogRepository extends JpaRepository<NewsletterSendLog, Long> {

    long countByCampaignIdAndStatus(UUID campaignId, String status);
}
