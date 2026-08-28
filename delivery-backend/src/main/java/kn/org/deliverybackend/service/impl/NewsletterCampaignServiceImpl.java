package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.newsletter.NewsletterCampaignDTO;
import kn.org.deliverybackend.dto.newsletter.NewsletterCampaignRequestDTO;
import kn.org.deliverybackend.entity.NewsletterCampaign;
import kn.org.deliverybackend.enumeration.CampaignAudience;
import kn.org.deliverybackend.enumeration.CampaignStatus;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.NewsletterCampaignRepository;
import kn.org.deliverybackend.service.NewsletterCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NewsletterCampaignServiceImpl implements NewsletterCampaignService {

    private final NewsletterCampaignRepository repository;
    private final NewsletterCampaignDispatcher dispatcher;

    @Override
    @Transactional
    public NewsletterCampaignDTO createDraft(NewsletterCampaignRequestDTO request) {
        CampaignAudience audience = CampaignAudience.parse(request.getAudience())
                .orElse(CampaignAudience.ALL_SUBSCRIBERS);

        NewsletterCampaign c = NewsletterCampaign.builder()
                .subject(request.getSubject().trim())
                .bodyHtml(request.getBodyHtml())
                .audience(audience.name())
                .status(CampaignStatus.DRAFT.name())
                .sentCount(0)
                .failCount(0)
                .build();
        return toDTO(repository.save(c));
    }

    @Override
    @Transactional
    public NewsletterCampaignDTO update(UUID id, NewsletterCampaignRequestDTO request) {
        NewsletterCampaign c = mustFind(id);
        if (!CampaignStatus.DRAFT.name().equals(c.getStatus())) {
            throw new InvalidStockOperationException(
                    "Only DRAFT campaigns can be edited (current: " + c.getStatus() + ").");
        }
        CampaignAudience audience = CampaignAudience.parse(request.getAudience())
                .orElse(CampaignAudience.ALL_SUBSCRIBERS);

        c.setSubject(request.getSubject().trim());
        c.setBodyHtml(request.getBodyHtml());
        c.setAudience(audience.name());
        return toDTO(repository.save(c));
    }

    @Override
    @Transactional
    public NewsletterCampaignDTO send(UUID id, String sentByIdentity) {
        NewsletterCampaign c = mustFind(id);
        if (!CampaignStatus.DRAFT.name().equals(c.getStatus())
                && !CampaignStatus.FAILED.name().equals(c.getStatus())) {
            throw new InvalidStockOperationException(
                    "Campaign cannot be sent from status " + c.getStatus() + ".");
        }
        // Flip to SENDING in this tx — the dispatcher reads after commit.
        c.setStatus(CampaignStatus.SENDING.name());
        c.setSentBy(sentByIdentity != null ? sentByIdentity : "admin");
        c.setSentAt(LocalDateTime.now());
        c.setSentCount(0);
        c.setFailCount(0);
        NewsletterCampaign saved = repository.save(c);

        // Fire-and-forget async dispatch; final status is written by the dispatcher.
        dispatcher.dispatch(saved.getId());

        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NewsletterCampaignDTO> list(int page, int size) {
        return repository.findAllForAdmin(PageRequest.of(page, size)).map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public NewsletterCampaignDTO get(UUID id) {
        return toDTO(mustFind(id));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        NewsletterCampaign c = mustFind(id);
        if (CampaignStatus.SENDING.name().equals(c.getStatus())) {
            throw new InvalidStockOperationException(
                    "Cannot delete a campaign that is currently sending.");
        }
        c.setDeleted(true);
        repository.save(c);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private NewsletterCampaign mustFind(UUID id) {
        return repository.findById(id)
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + id));
    }

    private NewsletterCampaignDTO toDTO(NewsletterCampaign c) {
        LocalDateTime created = c.getCreatedAt() == null ? null
                : LocalDateTime.ofInstant(c.getCreatedAt().toInstant(),
                        java.time.ZoneId.systemDefault());
        return NewsletterCampaignDTO.builder()
                .id(c.getId())
                .subject(c.getSubject())
                .bodyHtml(c.getBodyHtml())
                .audience(c.getAudience())
                .status(c.getStatus())
                .sentAt(c.getSentAt())
                .sentBy(c.getSentBy())
                .sentCount(c.getSentCount())
                .failCount(c.getFailCount())
                .createdAt(created)
                .build();
    }
}
