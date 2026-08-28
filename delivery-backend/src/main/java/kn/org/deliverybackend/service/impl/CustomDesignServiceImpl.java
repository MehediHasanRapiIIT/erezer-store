package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.customdesign.*;
import kn.org.deliverybackend.entity.CustomDesignAsset;
import kn.org.deliverybackend.entity.CustomDesignColor;
import kn.org.deliverybackend.entity.CustomDesignDraft;
import kn.org.deliverybackend.entity.CustomDesignItem;
import kn.org.deliverybackend.entity.CustomOrder;
import kn.org.deliverybackend.entity.CustomOrderImage;
import kn.org.deliverybackend.enumeration.CustomOrderStatus;
import kn.org.deliverybackend.event.CustomOrderCreatedEvent;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.CustomDesignAssetRepository;
import kn.org.deliverybackend.repository.CustomDesignDraftRepository;
import kn.org.deliverybackend.repository.CustomDesignItemRepository;
import kn.org.deliverybackend.repository.CustomOrderRepository;
import kn.org.deliverybackend.service.CustomDesignService;
import kn.org.deliverybackend.service.EmailAttachment;
import kn.org.deliverybackend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomDesignServiceImpl implements CustomDesignService {

    /** Canonical view order for previews (drives display + attachment order). */
    private static final Map<String, Integer> VIEW_ORDER =
            Map.of("front", 0, "back", 1, "leftsleeve", 2, "rightsleeve", 3);

    private final CustomDesignItemRepository itemRepository;
    private final CustomDesignAssetRepository assetRepository;
    private final CustomOrderRepository orderRepository;
    private final CustomDesignDraftRepository draftRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${minio.custom-design-bucket-name:custom-design-images}")
    private String bucket;

    // ── Assets ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public CustomDesignAssetsDTO getAssets() {
        List<CustomDesignItemDTO> items = itemRepository.findActive().stream()
                .map(this::toItemDTO)
                .toList();
        List<CustomDesignLogoDTO> logos = assetRepository.findActive().stream()
                .map(a -> CustomDesignLogoDTO.builder().name(a.getName()).url(a.getUrl()).build())
                .toList();
        return CustomDesignAssetsDTO.builder().items(items).logos(logos).build();
    }

    @Override
    public String uploadArtwork(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidStockOperationException("No file provided.");
        }
        return fileStorageService.uploadFile(file, bucket);
    }

    // ── Quote request ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CustomOrderDTO submitRequest(UUID userId, CustomOrderRequestDTO request, List<MultipartFile> previews) {
        CustomOrder order = CustomOrder.builder()
                .reference(generateReference())
                .userId(userId)
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phone(request.getPhone().trim())
                .email(request.getEmail().trim().toLowerCase())
                .shippingAddress(request.getShippingAddress().trim())
                .apartment(blankToNull(request.getApartment()))
                .city(request.getCity().trim())
                .zipCode(blankToNull(request.getZipCode()))
                .country(request.getCountry().trim())
                .notes(request.getNotes())
                .itemName(blankToNull(request.getItemName()))
                .colorName(blankToNull(request.getColorName()))
                .size(blankToNull(request.getSize()))
                .printTechnique(blankToNull(request.getPrintTechnique()))
                .designJson(request.getDesignJson())
                .status(CustomOrderStatus.NEW)
                .build();

        List<EmailAttachment> attachments = new ArrayList<>();
        if (previews != null) {
            for (MultipartFile preview : previews) {
                if (preview == null || preview.isEmpty()) {
                    continue;
                }
                String view = viewFromFilename(preview.getOriginalFilename());
                byte[] bytes;
                try {
                    bytes = preview.getBytes();
                } catch (Exception ex) {
                    log.warn("Could not read preview {} for custom order: {}",
                            preview.getOriginalFilename(), ex.getMessage());
                    continue;
                }
                String url = fileStorageService.uploadFile(preview, bucket);
                order.addImage(CustomOrderImage.builder()
                        .viewName(view)
                        .url(url)
                        .sortOrder(VIEW_ORDER.getOrDefault(view.toLowerCase(Locale.ROOT), 99))
                        .build());
                String contentType = preview.getContentType() == null ? "image/png" : preview.getContentType();
                attachments.add(new EmailAttachment(view + ".png", contentType, bytes));
            }
        }

        CustomOrder saved = orderRepository.save(order);

        eventPublisher.publishEvent(new CustomOrderCreatedEvent(
                this,
                saved.getId(),
                saved.getReference(),
                saved.getFirstName() + " " + saved.getLastName(),
                saved.getEmail(),
                saved.getPhone(),
                shippingBlock(saved),
                saved.getItemName(),
                saved.getColorName(),
                saved.getSize(),
                saved.getPrintTechnique(),
                saved.getNotes(),
                attachments));

        log.info("Custom order {} submitted ({} preview(s))", saved.getReference(), attachments.size());
        return toOrderDTO(saved);
    }

    // ── Drafts ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CustomDesignDraftDTO saveDraft(UUID userId, SaveDraftRequestDTO request) {
        CustomDesignDraft draft = CustomDesignDraft.builder()
                .userId(userId)
                .name(request.getName().trim())
                .itemName(blankToNull(request.getItemName()))
                .colorName(blankToNull(request.getColorName()))
                .thumbnailUrl(blankToNull(request.getThumbnailUrl()))
                .designJson(request.getDesignJson())
                .build();
        return toDraftDTO(draftRepository.save(draft), true);
    }

    @Override
    @Transactional
    public CustomDesignDraftDTO updateDraft(UUID userId, UUID draftId, SaveDraftRequestDTO request) {
        CustomDesignDraft draft = ownedDraft(userId, draftId);
        draft.setName(request.getName().trim());
        draft.setItemName(blankToNull(request.getItemName()));
        draft.setColorName(blankToNull(request.getColorName()));
        draft.setThumbnailUrl(blankToNull(request.getThumbnailUrl()));
        draft.setDesignJson(request.getDesignJson());
        return toDraftDTO(draftRepository.save(draft), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomDesignDraftDTO> listDrafts(UUID userId) {
        return draftRepository.findByUser(userId).stream()
                .map(d -> toDraftDTO(d, false))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomDesignDraftDTO getDraft(UUID userId, UUID draftId) {
        return toDraftDTO(ownedDraft(userId, draftId), true);
    }

    @Override
    @Transactional
    public void deleteDraft(UUID userId, UUID draftId) {
        CustomDesignDraft draft = ownedDraft(userId, draftId);
        draft.setDeleted(true);
        draftRepository.save(draft);
    }

    @Override
    @Transactional
    public CustomDesignDraftDTO shareDraft(UUID userId, UUID draftId) {
        CustomDesignDraft draft = ownedDraft(userId, draftId);
        if (draft.getShareToken() == null || draft.getShareToken().isBlank()) {
            draft.setShareToken(UUID.randomUUID().toString().replace("-", ""));
            draft = draftRepository.save(draft);
        }
        return toDraftDTO(draft, true);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomDesignDraftDTO getSharedDraft(String shareToken) {
        CustomDesignDraft draft = draftRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ResourceNotFoundException("Shared design not found."));
        return toDraftDTO(draft, true);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private CustomDesignDraft ownedDraft(UUID userId, UUID draftId) {
        return draftRepository.findOwned(draftId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Draft not found: " + draftId));
    }

    private String generateReference() {
        for (int i = 0; i < 10; i++) {
            String ref = "CD-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
            if (!orderRepository.existsByReference(ref)) {
                return ref;
            }
        }
        // Extremely unlikely; fall back to a longer, collision-proof reference.
        return "CD-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
    }

    /** Derives the view name from a preview file's base name (e.g. "front.png" → "front"). */
    private String viewFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "design";
        }
        String base = filename;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        return base.isBlank() ? "design" : base;
    }

    private String shippingBlock(CustomOrder o) {
        StringBuilder sb = new StringBuilder(o.getShippingAddress());
        if (o.getApartment() != null) sb.append(", ").append(o.getApartment());
        sb.append(", ").append(o.getCity());
        if (o.getZipCode() != null) sb.append(" ").append(o.getZipCode());
        sb.append(", ").append(o.getCountry());
        return sb.toString();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private CustomDesignItemDTO toItemDTO(CustomDesignItem item) {
        List<CustomDesignColorDTO> colors = item.getColors().stream()
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .map(this::toColorDTO)
                .toList();
        return CustomDesignItemDTO.builder()
                .name(item.getName())
                .category(item.getCategory())
                .sizes(new ArrayList<>(item.getSizes()))
                .printTechniques(new ArrayList<>(item.getPrintTechniques()))
                .colors(colors)
                .build();
    }

    private CustomDesignColorDTO toColorDTO(CustomDesignColor c) {
        return CustomDesignColorDTO.builder()
                .name(c.getName())
                .hex(c.getHex())
                .images(CustomDesignImagesDTO.builder()
                        .front(c.getFrontImageUrl())
                        .back(c.getBackImageUrl())
                        .leftSleeve(c.getLeftSleeveImageUrl())
                        .rightSleeve(c.getRightSleeveImageUrl())
                        .build())
                .build();
    }

    private CustomOrderDTO toOrderDTO(CustomOrder o) {
        List<CustomOrderImageDTO> images = o.getImages().stream()
                .map(img -> CustomOrderImageDTO.builder().view(img.getViewName()).url(img.getUrl()).build())
                .toList();
        return CustomOrderDTO.builder()
                .id(o.getId())
                .reference(o.getReference())
                .firstName(o.getFirstName())
                .lastName(o.getLastName())
                .phone(o.getPhone())
                .email(o.getEmail())
                .shippingAddress(o.getShippingAddress())
                .apartment(o.getApartment())
                .city(o.getCity())
                .zipCode(o.getZipCode())
                .country(o.getCountry())
                .notes(o.getNotes())
                .itemName(o.getItemName())
                .colorName(o.getColorName())
                .size(o.getSize())
                .printTechnique(o.getPrintTechnique())
                .designJson(o.getDesignJson())
                .status(o.getStatus().name())
                .adminNotes(o.getAdminNotes())
                .images(images)
                .createdAt(toLdt(o.getCreatedAt()))
                .build();
    }

    private CustomDesignDraftDTO toDraftDTO(CustomDesignDraft d, boolean includeJson) {
        return CustomDesignDraftDTO.builder()
                .id(d.getId())
                .name(d.getName())
                .itemName(d.getItemName())
                .colorName(d.getColorName())
                .thumbnailUrl(d.getThumbnailUrl())
                .designJson(includeJson ? d.getDesignJson() : null)
                .shareToken(d.getShareToken())
                .updatedAt(toLdt(d.getUpdatedAt()))
                .build();
    }

    private static LocalDateTime toLdt(java.util.Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
}
