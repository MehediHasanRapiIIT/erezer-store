package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.customdesign.CustomDesignAssetsDTO;
import kn.org.deliverybackend.dto.customdesign.CustomDesignDraftDTO;
import kn.org.deliverybackend.dto.customdesign.CustomOrderDTO;
import kn.org.deliverybackend.dto.customdesign.CustomOrderRequestDTO;
import kn.org.deliverybackend.dto.customdesign.SaveDraftRequestDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Public (storefront) custom-design operations: studio assets, quote requests, drafts. */
public interface CustomDesignService {

    /** Designable garments + logo library for the studio. */
    CustomDesignAssetsDTO getAssets();

    /** Stores an uploaded artwork file (customer's own image) and returns its URL. */
    String uploadArtwork(MultipartFile file);

    /**
     * Persists a "Submit for Price" request, stores the flattened per-view
     * previews, and triggers the admin notification email.
     *
     * @param userId   the logged-in customer, or {@code null} for a guest
     * @param previews flattened preview PNGs; each file's base name is its view
     */
    CustomOrderDTO submitRequest(UUID userId, CustomOrderRequestDTO request, List<MultipartFile> previews);

    // ── Drafts (logged-in customers) ────────────────────────────────────────
    CustomDesignDraftDTO saveDraft(UUID userId, SaveDraftRequestDTO request);

    CustomDesignDraftDTO updateDraft(UUID userId, UUID draftId, SaveDraftRequestDTO request);

    List<CustomDesignDraftDTO> listDrafts(UUID userId);

    CustomDesignDraftDTO getDraft(UUID userId, UUID draftId);

    void deleteDraft(UUID userId, UUID draftId);

    /** Creates (or returns the existing) public share token for a draft. */
    CustomDesignDraftDTO shareDraft(UUID userId, UUID draftId);

    /** Public read-only lookup of a shared draft by token. */
    CustomDesignDraftDTO getSharedDraft(String shareToken);
}
