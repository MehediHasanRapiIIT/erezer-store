package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.customdesign.CustomDesignItemAdminDTO;
import kn.org.deliverybackend.dto.customdesign.CustomDesignLogoAdminDTO;
import kn.org.deliverybackend.dto.customdesign.CustomOrderDTO;
import kn.org.deliverybackend.dto.customdesign.CustomOrderStatusUpdateDTO;
import kn.org.deliverybackend.dto.customdesign.CustomOrderSummaryDTO;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

/** Admin management of the custom-design studio assets and submitted quote requests. */
public interface CustomDesignAdminService {

    // ── Garments ────────────────────────────────────────────────────────────
    List<CustomDesignItemAdminDTO> listItems();

    CustomDesignItemAdminDTO upsertItem(CustomDesignItemAdminDTO dto);

    void deleteItem(UUID id);

    // ── Logo library ────────────────────────────────────────────────────────
    List<CustomDesignLogoAdminDTO> listLogos();

    CustomDesignLogoAdminDTO upsertLogo(CustomDesignLogoAdminDTO dto);

    void deleteLogo(UUID id);

    // ── Quote requests ──────────────────────────────────────────────────────
    Page<CustomOrderSummaryDTO> listOrders(String status, int page, int size, boolean history);

    CustomOrderDTO getOrder(UUID id);

    CustomOrderDTO updateOrderStatus(UUID id, CustomOrderStatusUpdateDTO update);

    void deleteOrder(UUID id);
}
