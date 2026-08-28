package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.customdesign.CustomDesignColorAdminDTO;
import kn.org.deliverybackend.dto.customdesign.CustomDesignItemAdminDTO;
import kn.org.deliverybackend.dto.customdesign.CustomDesignLogoAdminDTO;
import kn.org.deliverybackend.dto.customdesign.CustomOrderDTO;
import kn.org.deliverybackend.dto.customdesign.CustomOrderImageDTO;
import kn.org.deliverybackend.dto.customdesign.CustomOrderStatusUpdateDTO;
import kn.org.deliverybackend.dto.customdesign.CustomOrderSummaryDTO;
import kn.org.deliverybackend.entity.CustomDesignAsset;
import kn.org.deliverybackend.entity.CustomDesignColor;
import kn.org.deliverybackend.entity.CustomDesignItem;
import kn.org.deliverybackend.entity.CustomOrder;
import kn.org.deliverybackend.enumeration.CustomOrderStatus;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.CustomDesignAssetRepository;
import kn.org.deliverybackend.repository.CustomDesignItemRepository;
import kn.org.deliverybackend.repository.CustomOrderRepository;
import kn.org.deliverybackend.service.CustomDesignAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomDesignAdminServiceImpl implements CustomDesignAdminService {

    private final CustomDesignItemRepository itemRepository;
    private final CustomDesignAssetRepository assetRepository;
    private final CustomOrderRepository orderRepository;

    // ── Garments ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<CustomDesignItemAdminDTO> listItems() {
        return itemRepository.findAllForAdmin().stream().map(this::toItemAdminDTO).toList();
    }

    @Override
    @Transactional
    public CustomDesignItemAdminDTO upsertItem(CustomDesignItemAdminDTO dto) {
        CustomDesignItem item = dto.getId() == null
                ? new CustomDesignItem()
                : itemRepository.findById(dto.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Garment not found: " + dto.getId()));

        item.setName(dto.getName().trim());
        item.setCategory(dto.getCategory() == null ? null : dto.getCategory().trim());
        item.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        item.setActive(dto.getActive() == null ? Boolean.TRUE : dto.getActive());
        item.setSizes(dto.getSizes() == null ? new ArrayList<>() : new ArrayList<>(dto.getSizes()));
        item.setPrintTechniques(dto.getPrintTechniques() == null ? new ArrayList<>() : new ArrayList<>(dto.getPrintTechniques()));

        // Colours are fully replaced from the payload (orphanRemoval deletes the old rows).
        item.getColors().clear();
        int order = 0;
        if (dto.getColors() != null) {
            for (CustomDesignColorAdminDTO c : dto.getColors()) {
                CustomDesignColor color = CustomDesignColor.builder()
                        .name(c.getName().trim())
                        .hex(c.getHex().trim())
                        .frontImageUrl(c.getFrontImageUrl())
                        .backImageUrl(c.getBackImageUrl())
                        .leftSleeveImageUrl(c.getLeftSleeveImageUrl())
                        .rightSleeveImageUrl(c.getRightSleeveImageUrl())
                        .sortOrder(c.getSortOrder() == null ? order : c.getSortOrder())
                        .build();
                item.addColor(color);
                order++;
            }
        }
        return toItemAdminDTO(itemRepository.save(item));
    }

    @Override
    @Transactional
    public void deleteItem(UUID id) {
        CustomDesignItem item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Garment not found: " + id));
        item.setDeleted(true);
        item.setActive(false);
        itemRepository.save(item);
    }

    // ── Logo library ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<CustomDesignLogoAdminDTO> listLogos() {
        return assetRepository.findAllForAdmin().stream().map(this::toLogoAdminDTO).toList();
    }

    @Override
    @Transactional
    public CustomDesignLogoAdminDTO upsertLogo(CustomDesignLogoAdminDTO dto) {
        CustomDesignAsset asset = dto.getId() == null
                ? new CustomDesignAsset()
                : assetRepository.findById(dto.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Logo not found: " + dto.getId()));
        asset.setName(dto.getName().trim());
        asset.setUrl(dto.getUrl().trim());
        asset.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        asset.setActive(dto.getActive() == null ? Boolean.TRUE : dto.getActive());
        return toLogoAdminDTO(assetRepository.save(asset));
    }

    @Override
    @Transactional
    public void deleteLogo(UUID id) {
        CustomDesignAsset asset = assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Logo not found: " + id));
        asset.setDeleted(true);
        asset.setActive(false);
        assetRepository.save(asset);
    }

    // ── Quote requests ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<CustomOrderSummaryDTO> listOrders(String status, int page, int size, boolean history) {
        PageRequest pageable = PageRequest.of(page, size);
        // History = delivered orders only.
        if (history) {
            return orderRepository.findForAdmin(CustomOrderStatus.DELIVERED, pageable).map(this::toSummaryDTO);
        }
        CustomOrderStatus parsed = (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL"))
                ? parseStatus(status) : null;
        // Active list with a specific status filter.
        if (parsed != null) {
            return orderRepository.findForAdmin(parsed, pageable).map(this::toSummaryDTO);
        }
        // Active list, no filter → everything except delivered (those live in history).
        return orderRepository.findForAdminExcluding(CustomOrderStatus.DELIVERED, pageable).map(this::toSummaryDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomOrderDTO getOrder(UUID id) {
        return toOrderDTO(loadOrder(id));
    }

    @Override
    @Transactional
    public CustomOrderDTO updateOrderStatus(UUID id, CustomOrderStatusUpdateDTO update) {
        CustomOrder order = loadOrder(id);
        order.setStatus(parseStatus(update.getStatus()));
        if (update.getAdminNotes() != null) {
            order.setAdminNotes(update.getAdminNotes());
        }
        return toOrderDTO(orderRepository.save(order));
    }

    @Override
    @Transactional
    public void deleteOrder(UUID id) {
        CustomOrder order = loadOrder(id);
        order.setDeleted(true);
        orderRepository.save(order);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private CustomOrder loadOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Custom order not found: " + id));
    }

    private CustomOrderStatus parseStatus(String status) {
        try {
            return CustomOrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidStockOperationException("Unknown custom order status: " + status);
        }
    }

    private CustomDesignItemAdminDTO toItemAdminDTO(CustomDesignItem item) {
        List<CustomDesignColorAdminDTO> colors = item.getColors().stream()
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .map(c -> CustomDesignColorAdminDTO.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .hex(c.getHex())
                        .frontImageUrl(c.getFrontImageUrl())
                        .backImageUrl(c.getBackImageUrl())
                        .leftSleeveImageUrl(c.getLeftSleeveImageUrl())
                        .rightSleeveImageUrl(c.getRightSleeveImageUrl())
                        .sortOrder(c.getSortOrder())
                        .build())
                .toList();
        return CustomDesignItemAdminDTO.builder()
                .id(item.getId())
                .name(item.getName())
                .category(item.getCategory())
                .sortOrder(item.getSortOrder())
                .active(item.getActive())
                .sizes(new ArrayList<>(item.getSizes()))
                .printTechniques(new ArrayList<>(item.getPrintTechniques()))
                .colors(colors)
                .build();
    }

    private CustomDesignLogoAdminDTO toLogoAdminDTO(CustomDesignAsset a) {
        return CustomDesignLogoAdminDTO.builder()
                .id(a.getId())
                .name(a.getName())
                .url(a.getUrl())
                .sortOrder(a.getSortOrder())
                .active(a.getActive())
                .build();
    }

    private CustomOrderSummaryDTO toSummaryDTO(CustomOrder o) {
        String thumb = o.getImages().stream()
                .min((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(img -> img.getUrl())
                .orElse(null);
        return CustomOrderSummaryDTO.builder()
                .id(o.getId())
                .reference(o.getReference())
                .customerName(o.getFirstName() + " " + o.getLastName())
                .email(o.getEmail())
                .phone(o.getPhone())
                .itemName(o.getItemName())
                .status(o.getStatus().name())
                .thumbnailUrl(thumb)
                .createdAt(toLdt(o.getCreatedAt()))
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

    private static LocalDateTime toLdt(java.util.Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
}
