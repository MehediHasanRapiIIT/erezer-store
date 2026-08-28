package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.newsletter.NewsletterSubscribeRequestDTO;
import kn.org.deliverybackend.dto.newsletter.NewsletterSubscriberDTO;
import kn.org.deliverybackend.entity.NewsletterSubscriber;
import kn.org.deliverybackend.enumeration.SubscriberStatus;
import kn.org.deliverybackend.repository.NewsletterSubscriberRepository;
import kn.org.deliverybackend.service.NewsletterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NewsletterServiceImpl implements NewsletterService {

    private final NewsletterSubscriberRepository repository;

    @Override
    @Transactional
    public NewsletterSubscriberDTO subscribe(NewsletterSubscribeRequestDTO request) {
        String email = request.getEmail().trim().toLowerCase();
        NewsletterSubscriber sub = repository.findByEmailIgnoreCase(email).orElse(null);

        if (sub == null) {
            sub = NewsletterSubscriber.builder()
                    .email(email)
                    .status(SubscriberStatus.SUBSCRIBED.name())
                    .source(request.getSource())
                    .unsubscribeToken(UUID.randomUUID().toString().replace("-", ""))
                    .subscribedAt(LocalDateTime.now())
                    .build();
        } else if (SubscriberStatus.UNSUBSCRIBED.name().equals(sub.getStatus())) {
            // Re-subscribe — keep the same token so old emails still unsubscribe correctly.
            sub.setStatus(SubscriberStatus.SUBSCRIBED.name());
            sub.setUnsubscribedAt(null);
            sub.setSubscribedAt(LocalDateTime.now());
            if (request.getSource() != null) sub.setSource(request.getSource());
        }
        return toDTO(repository.save(sub));
    }

    @Override
    @Transactional
    public void unsubscribeByToken(String token) {
        if (token == null || token.isBlank()) return;
        repository.findByUnsubscribeToken(token).ifPresent(sub -> {
            if (SubscriberStatus.SUBSCRIBED.name().equals(sub.getStatus())) {
                sub.setStatus(SubscriberStatus.UNSUBSCRIBED.name());
                sub.setUnsubscribedAt(LocalDateTime.now());
                repository.save(sub);
                log.info("Newsletter unsubscribe: {}", sub.getEmail());
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NewsletterSubscriberDTO> list(String status, int page, int size) {
        String normalized = (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL"))
                ? status.toUpperCase() : null;
        return repository.findForAdmin(normalized, PageRequest.of(page, size)).map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveSubscribers() {
        return repository.countByStatusAndDeletedFalse(SubscriberStatus.SUBSCRIBED.name());
    }

    private NewsletterSubscriberDTO toDTO(NewsletterSubscriber s) {
        return NewsletterSubscriberDTO.builder()
                .id(s.getId())
                .email(s.getEmail())
                .status(s.getStatus())
                .source(s.getSource())
                .subscribedAt(s.getSubscribedAt())
                .unsubscribedAt(s.getUnsubscribedAt())
                .build();
    }
}
