package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.newsletter.NewsletterSubscribeRequestDTO;
import kn.org.deliverybackend.dto.newsletter.NewsletterSubscriberDTO;
import org.springframework.data.domain.Page;

public interface NewsletterService {

    /** Public subscribe — idempotent: re-subscribes a previously-unsubscribed row. */
    NewsletterSubscriberDTO subscribe(NewsletterSubscribeRequestDTO request);

    /** Public unsubscribe via token — never throws "not found", to avoid email enumeration. */
    void unsubscribeByToken(String token);

    /** Admin paginated list, optionally filtered by status (SUBSCRIBED / UNSUBSCRIBED). */
    /** Subscribers, newest first; {@code q} searches the email. */
    Page<NewsletterSubscriberDTO> list(String status, String q, int page, int size);

    long countActiveSubscribers();
}
