package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.returns.ReturnDecisionDTO;
import kn.org.deliverybackend.dto.returns.ReturnRequestCreateDTO;
import kn.org.deliverybackend.dto.returns.ReturnRequestDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ReturnService {

    // ── Customer ───────────────────────────────────────────────────────────────

    /** Create a return request for an order the caller owns. Enforces the return window. */
    ReturnRequestDTO requestReturn(UUID userId,
                                    UUID orderId,
                                    ReturnRequestCreateDTO request,
                                    List<MultipartFile> photos);

    List<ReturnRequestDTO> listMine(UUID userId);

    ReturnRequestDTO getMine(UUID userId, UUID returnId);

    // ── Admin ──────────────────────────────────────────────────────────────────

    org.springframework.data.domain.Page<ReturnRequestDTO> listForAdmin(String status, int page, int size);

    ReturnRequestDTO getForAdmin(UUID returnId);

    ReturnRequestDTO approve(UUID returnId, ReturnDecisionDTO decision, String adminIdentity);

    ReturnRequestDTO reject(UUID returnId, ReturnDecisionDTO decision, String adminIdentity);

    ReturnRequestDTO markPickedUp(UUID returnId, String adminIdentity);

    /** Marks REFUNDED; transitions the parent order to RETURNED. */
    ReturnRequestDTO markRefunded(UUID returnId, ReturnDecisionDTO decision, String adminIdentity);
}
