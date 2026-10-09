package kn.org.deliverybackend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kn.org.deliverybackend.dto.settings.ContentPageDTO;
import kn.org.deliverybackend.entity.ContentPage;
import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.ContentPageRepository;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The shop's own pages ("Our Mission", "Our Values", ...), and their links in
 * the footer.
 *
 * <p>The footer's links are kept with the footer, where the shop can still
 * arrange them by hand. A page's "show in the footer" switch only puts its link
 * there or takes it out: saving a page keeps its link in step with its title
 * and address, and a page that is hidden or deleted loses its link.
 */
@Service
@RequiredArgsConstructor
public class ContentPageService {

    /** The most sections one page has. */
    public static final int MAX_SECTIONS = 30;
    /** Addresses the shop already uses under /pages or would be confused with. */
    private static final Set<String> RESERVED = Set.of("new", "edit", "admin");

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ContentPageRepository repository;
    private final StoreSettingsRepository settingsRepository;

    // ── reading ──────────────────────────────────────────────────────────────

    /** The pages customers can open, in the shop's order. */
    @Transactional(readOnly = true)
    public List<ContentPageDTO> listActive() {
        return repository.findLive().stream().filter(p -> Boolean.TRUE.equals(p.getIsActive())).map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public ContentPageDTO getActive(String slug) {
        return repository.findLiveBySlug(slug == null ? "" : slug.trim())
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .map(this::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Page not found: " + slug));
    }

    /** Every page, switched off or not, for the admin panel. */
    @Transactional(readOnly = true)
    public List<ContentPageDTO> listAll() {
        return repository.findLive().stream().map(this::toDTO).toList();
    }

    // ── changing ─────────────────────────────────────────────────────────────

    @Transactional
    public ContentPageDTO create(ContentPageDTO request) {
        ContentPage page = new ContentPage();
        apply(page, request, null);
        if (request.getSortOrder() == null) {
            page.setSortOrder(repository.findLive().stream().mapToInt(p -> p.getSortOrder() == null ? 0 : p.getSortOrder()).max().orElse(-1) + 1);
        }
        ContentPage saved = repository.save(page);
        syncFooter(null, saved);
        return toDTO(saved);
    }

    @Transactional
    public ContentPageDTO update(Long id, ContentPageDTO request) {
        ContentPage page = live(id);
        String addressBefore = address(page.getSlug());
        apply(page, request, id);
        ContentPage saved = repository.save(page);
        syncFooter(addressBefore, saved);
        return toDTO(saved);
    }

    @Transactional
    public void delete(Long id) {
        ContentPage page = live(id);
        page.setDeleted(true);
        page.setDeletedAt(new java.util.Date());
        page.setIsActive(false);
        repository.save(page);
        syncFooter(address(page.getSlug()), page);
    }

    private void apply(ContentPage page, ContentPageDTO r, Long selfId) {
        String title = clean(r.getTitle());
        if (title == null) throw new InvalidRequestException("Give the page a title.");
        page.setTitle(title);
        page.setSlug(freeSlug(clean(r.getSlug()) != null ? r.getSlug() : title, selfId));
        page.setEyebrow(clean(r.getEyebrow()));
        page.setIntro(text(r.getIntro()));
        page.setHeroImageUrl(clean(r.getHeroImageUrl()));
        page.setClosing(text(r.getClosing()));

        String label = clean(r.getCtaLabel());
        String link = clean(r.getCtaLink());
        if ((label == null) != (link == null)) {
            throw new InvalidRequestException("The button needs both its words and where it goes, or neither.");
        }
        page.setCtaLabel(label);
        page.setCtaLink(link);

        List<ContentPageDTO.Section> sections = new ArrayList<>();
        for (ContentPageDTO.Section s : r.getSections() == null ? List.<ContentPageDTO.Section>of() : r.getSections()) {
            if (s == null) continue;
            String heading = clean(s.getHeading());
            String body = text(s.getBody());
            String image = clean(s.getImageUrl());
            if (heading == null && body == null && image == null) continue;
            if (heading != null && heading.length() > 200) throw new InvalidRequestException("A section's heading can be up to 200 letters.");
            if (body != null && body.length() > 5000) throw new InvalidRequestException("A section's text can be up to 5000 letters.");
            sections.add(new ContentPageDTO.Section(heading, body, image));
        }
        if (sections.size() > MAX_SECTIONS) throw new InvalidRequestException("Up to " + MAX_SECTIONS + " sections on a page.");
        try {
            page.setSectionsJson(JSON.writeValueAsString(sections));
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("That page could not be saved.");
        }
        if (r.getShowInFooter() != null || page.getShowInFooter() == null) {
            page.setShowInFooter(r.getShowInFooter() == null || r.getShowInFooter());
        }
        if (r.getIsActive() != null || page.getIsActive() == null) {
            page.setIsActive(r.getIsActive() == null || r.getIsActive());
        }
        if (r.getSortOrder() != null) page.setSortOrder(r.getSortOrder());
        if (page.getSortOrder() == null) page.setSortOrder(0);
    }

    /**
     * An address made of small letters, digits and hyphens, that no other page
     * has: "Our Mission!" becomes "our-mission", and a second page of that name
     * "our-mission-2".
     */
    private String freeSlug(String raw, Long selfId) {
        String base = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
        if (base.isBlank()) base = "page";
        if (base.length() > 120) base = base.substring(0, 120);
        if (RESERVED.contains(base)) base = base + "-page";
        String candidate = base;
        for (int n = 2; taken(candidate, selfId); n++) candidate = base + "-" + n;
        return candidate;
    }

    private boolean taken(String slug, Long selfId) {
        return repository.findLiveBySlug(slug).filter(p -> !p.getId().equals(selfId)).isPresent();
    }

    private ContentPage live(Long id) {
        return repository.findLive(id).orElseThrow(() -> new ResourceNotFoundException("Page not found: " + id));
    }

    // ── the footer ───────────────────────────────────────────────────────────

    public static String address(String slug) {
        return "/pages/" + slug;
    }

    /**
     * Keeps a page's link in the footer in step with the page: there, with the
     * page's title, while the page is live, switched on and set to show in the
     * footer; gone otherwise. A link the shop moved to another column stays
     * where it was put.
     */
    private void syncFooter(String addressBefore, ContentPage page) {
        StoreSettings settings = settingsRepository.findById(StoreSettings.SINGLETON_ID).orElse(null);
        if (settings == null || settings.getFooterJson() == null || settings.getFooterJson().isBlank()) return;
        ObjectNode footer;
        try {
            JsonNode parsed = JSON.readTree(settings.getFooterJson());
            if (!(parsed instanceof ObjectNode node)) return;
            footer = node;
        } catch (JsonProcessingException e) {
            return;
        }
        ArrayNode columns = footer.get("columns") instanceof ArrayNode a ? a : footer.putArray("columns");
        String addressNow = address(page.getSlug());
        boolean wanted = !Boolean.TRUE.equals(page.getDeleted()) && Boolean.TRUE.equals(page.getIsActive())
                && Boolean.TRUE.equals(page.getShowInFooter());

        boolean placed = false;
        for (JsonNode column : columns) {
            if (!(column.get("links") instanceof ArrayNode links)) continue;
            for (int i = links.size() - 1; i >= 0; i--) {
                String url = links.get(i).path("url").asText("");
                if (!url.equals(addressNow) && !url.equals(addressBefore)) continue;
                if (wanted && !placed) {
                    ObjectNode link = (ObjectNode) links.get(i);
                    link.put("label", page.getTitle());
                    link.put("url", addressNow);
                    placed = true;
                } else {
                    links.remove(i);
                }
            }
        }
        if (wanted && !placed) {
            ObjectNode first = columns.size() > 0 && columns.get(0) instanceof ObjectNode c ? c : columns.addObject().put("title", "Company");
            ArrayNode links = first.get("links") instanceof ArrayNode l ? l : first.putArray("links");
            links.addObject().put("label", page.getTitle()).put("url", addressNow);
        }
        settings.setFooterJson(footer.toString());
        settingsRepository.save(settings);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ContentPageDTO toDTO(ContentPage p) {
        return ContentPageDTO.builder()
                .id(p.getId())
                .slug(p.getSlug())
                .title(p.getTitle())
                .eyebrow(p.getEyebrow())
                .intro(p.getIntro())
                .heroImageUrl(p.getHeroImageUrl())
                .sections(sectionsOf(p))
                .closing(p.getClosing())
                .ctaLabel(p.getCtaLabel())
                .ctaLink(p.getCtaLink())
                .showInFooter(Boolean.TRUE.equals(p.getShowInFooter()))
                .isActive(Boolean.TRUE.equals(p.getIsActive()))
                .sortOrder(p.getSortOrder() == null ? 0 : p.getSortOrder())
                .build();
    }

    private static List<ContentPageDTO.Section> sectionsOf(ContentPage p) {
        if (p.getSectionsJson() == null || p.getSectionsJson().isBlank()) return new ArrayList<>();
        try {
            return JSON.readValue(p.getSectionsJson(), new TypeReference<List<ContentPageDTO.Section>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    /** One line, trimmed; null when empty. */
    private static String clean(String raw) {
        if (raw == null) return null;
        String text = raw.trim().replaceAll("[ \\t]+", " ");
        return text.isEmpty() ? null : text;
    }

    /** Text that may run over several paragraphs, trimmed; null when empty. */
    private static String text(String raw) {
        if (raw == null) return null;
        String text = raw.replace("\r\n", "\n").trim();
        return text.isEmpty() ? null : text;
    }
}
