package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.newsletter.NewsletterCampaignDTO;
import kn.org.deliverybackend.dto.newsletter.NewsletterCampaignRequestDTO;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface NewsletterCampaignService {

    NewsletterCampaignDTO createDraft(NewsletterCampaignRequestDTO request);

    NewsletterCampaignDTO update(UUID id, NewsletterCampaignRequestDTO request);

    /**
     * Marks SENDING and fans-out async sends through {@code EmailService}.
     * Returns immediately with the in-flight campaign DTO; final status appears
     * after the async worker finishes.
     */
    NewsletterCampaignDTO send(UUID id, String sentByIdentity);

    Page<NewsletterCampaignDTO> list(int page, int size);

    NewsletterCampaignDTO get(UUID id);

    void delete(UUID id);
}
