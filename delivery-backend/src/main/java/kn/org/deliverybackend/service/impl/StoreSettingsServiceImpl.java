package kn.org.deliverybackend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kn.org.deliverybackend.dto.settings.BrandStoryDTO;
import kn.org.deliverybackend.dto.settings.FooterColumnDTO;
import kn.org.deliverybackend.dto.settings.FooterDTO;
import kn.org.deliverybackend.dto.settings.FooterLinkDTO;
import kn.org.deliverybackend.dto.settings.FooterOutletDTO;
import kn.org.deliverybackend.dto.settings.FooterPromiseDTO;
import kn.org.deliverybackend.dto.settings.HighlightDTO;
import kn.org.deliverybackend.dto.settings.MarqueeDTO;
import kn.org.deliverybackend.dto.settings.SizeChartCellDTO;
import kn.org.deliverybackend.dto.settings.SizeChartDTO;
import kn.org.deliverybackend.dto.settings.SizeChartRowDTO;
import kn.org.deliverybackend.dto.settings.StoreSettingsDTO;
import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import kn.org.deliverybackend.service.StoreSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StoreSettingsServiceImpl implements StoreSettingsService {

    private final StoreSettingsRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public StoreSettingsDTO get() {
        StoreSettings settings = repository.findById(StoreSettings.SINGLETON_ID)
                .orElseGet(this::seedDefaults);
        // Backfill content added after a row already existed (e.g. brand story /
        // footer on a DB seeded by an earlier version), so the storefront always
        // gets sensible defaults until an admin customises them.
        boolean changed = false;
        if (settings.getBrandStoryJson() == null || settings.getBrandStoryJson().isBlank()) {
            settings.setBrandStoryJson(write(defaultBrandStory()));
            changed = true;
        }
        if (settings.getFooterJson() == null || settings.getFooterJson().isBlank()) {
            settings.setFooterJson(write(defaultFooter()));
            changed = true;
        }
        if (settings.getMarqueeJson() == null || settings.getMarqueeJson().isBlank()) {
            settings.setMarqueeJson(write(defaultMarquee()));
            changed = true;
        }
        if (settings.getSizeChartJson() == null || settings.getSizeChartJson().isBlank()) {
            settings.setSizeChartJson(write(defaultChart()));
            changed = true;
        }
        if (settings.getHighlightsJson() == null || settings.getHighlightsJson().isBlank()) {
            settings.setHighlightsJson(write(defaultHighlights()));
            changed = true;
        }
        if (changed) {
            settings = repository.save(settings);
        }
        return toDTO(settings);
    }

    @Override
    @Transactional
    public StoreSettingsDTO update(StoreSettingsDTO request) {
        StoreSettings settings = repository.findById(StoreSettings.SINGLETON_ID)
                .orElseGet(() -> StoreSettings.builder().id(StoreSettings.SINGLETON_ID).build());

        settings.setReturnPolicyText(request.getReturnPolicyText());
        settings.setExchangeWindowDays(request.getExchangeWindowDays());
        settings.setSupportPhone(request.getSupportPhone());
        settings.setSupportEmail(request.getSupportEmail());
        settings.setSupportHours(request.getSupportHours());
        settings.setSizeChartJson(write(request.getSizeChart()));
        settings.setBrandStoryJson(write(request.getBrandStory()));
        settings.setFooterJson(write(request.getFooter()));
        settings.setMarqueeJson(write(request.getMarquee()));
        settings.setHighlightsJson(write(request.getHighlights()));
        // Payment toggles: null in request → default enabled.
        settings.setPaymentCodEnabled(request.getPaymentCodEnabled() == null || request.getPaymentCodEnabled());
        settings.setPaymentBkashEnabled(request.getPaymentBkashEnabled() == null || request.getPaymentBkashEnabled());
        settings.setPaymentCardEnabled(request.getPaymentCardEnabled() == null || request.getPaymentCardEnabled());

        return toDTO(repository.save(settings));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private StoreSettings seedDefaults() {
        StoreSettings settings = StoreSettings.builder()
                .id(StoreSettings.SINGLETON_ID)
                .returnPolicyText("Changed your mind? Tell us within 3 days of delivery to start a "
                        + "return or exchange. Items must be unworn, unwashed and have their original "
                        + "tags attached. Contact our support team to arrange a pickup.")
                .exchangeWindowDays(3)
                .supportPhone("+880 1700-000000")
                .supportEmail("support@erezer.com")
                .supportHours("Sat–Thu, 10:00–19:00")
                .sizeChartJson(write(defaultChart()))
                .brandStoryJson(write(defaultBrandStory()))
                .footerJson(write(defaultFooter()))
                .marqueeJson(write(defaultMarquee()))
                .highlightsJson(write(defaultHighlights()))
                .paymentCodEnabled(true)
                .paymentBkashEnabled(true)
                .paymentCardEnabled(true)
                .build();
        return repository.save(settings);
    }

    private List<HighlightDTO> defaultHighlights() {
        return List.of(
                HighlightDTO.builder().icon("star").value("4.9 / 5")
                        .label("Customer rating")
                        .description("Loved by shoppers across Bangladesh.").build(),
                HighlightDTO.builder().icon("truck").value("2–4 days")
                        .label("Fast delivery")
                        .description("Reliable nationwide shipping.").build(),
                HighlightDTO.builder().icon("refresh").value("3-day")
                        .label("Easy exchanges")
                        .description("Tell us within 3 days to return or exchange.").build(),
                HighlightDTO.builder().icon("shield").value("256-bit SSL")
                        .label("Secure checkout")
                        .description("Protected payments, end to end.").build());
    }

    private MarqueeDTO defaultMarquee() {
        return MarqueeDTO.builder()
                .enabled(true)
                .items(List.of(
                        "Free shipping over ৳2000",
                        "bKash accepted",
                        "3-day easy exchange",
                        "Premium materials",
                        "Secure checkout",
                        "Made for everyday"))
                .build();
    }

    private SizeChartDTO defaultChart() {
        return SizeChartDTO.builder()
                .columns(List.of("Chest", "Length"))
                .rows(List.of(
                        row("S",   96,  37.8,  68,  26.8),
                        row("M",  101,  39.8,  70,  27.6),
                        row("L",  106,  41.7,  72,  28.3),
                        row("XL", 111,  43.7,  74,  29.1),
                        row("XXL",116,  45.7,  76,  29.9)
                ))
                .build();
    }

    private BrandStoryDTO defaultBrandStory() {
        return BrandStoryDTO.builder()
                .eyebrow("Our story")
                .heading("Considered clothing, made to last.")
                .body("Erezer is built on timeless silhouettes, premium fabrics and honest pricing — "
                        + "pieces designed to live in your wardrobe for years, not seasons.")
                .ctaLabel("Explore the collection")
                .ctaLink("/shop")
                .socialHandle("@erezer")
                .socialUrl("https://instagram.com/erezer")
                .images(List.of(
                        "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?auto=format&fit=crop&w=500&q=80",
                        "https://images.unsplash.com/photo-1539109136881-3be0616acf4b?auto=format&fit=crop&w=500&q=80",
                        "https://images.unsplash.com/photo-1503342217505-b0a15ec3261c?auto=format&fit=crop&w=500&q=80",
                        "https://images.unsplash.com/photo-1485231183945-fffde7cc051e?auto=format&fit=crop&w=500&q=80",
                        "https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?auto=format&fit=crop&w=500&q=80",
                        "https://images.unsplash.com/photo-1496747611176-843222e1e57c?auto=format&fit=crop&w=500&q=80"))
                .build();
    }

    private FooterDTO defaultFooter() {
        return FooterDTO.builder()
                .brandName("EREZER")
                .blurb("Minimal essentials for a modern wardrobe. Thoughtful design, premium comfort, timeless style.")
                .columns(List.of(
                        FooterColumnDTO.builder().title("Company").links(List.of(
                                FooterLinkDTO.builder().label("About").url("/about").build(),
                                FooterLinkDTO.builder().label("Journal").url("/journal").build(),
                                FooterLinkDTO.builder().label("Careers").url("/careers").build()
                        )).build(),
                        FooterColumnDTO.builder().title("Support").links(List.of(
                                FooterLinkDTO.builder().label("Shipping & Returns").url("/shipping").build(),
                                FooterLinkDTO.builder().label("Help Center").url("/contact").build(),
                                FooterLinkDTO.builder().label("care@erezer.com").url("mailto:care@erezer.com").build()
                        )).build()))
                .promises(List.of(
                        FooterPromiseDTO.builder().icon("quality")
                                .title("Comfort & Quality Assured")
                                .description("Thoughtfully selected with quality finishing.").build(),
                        FooterPromiseDTO.builder().icon("support")
                                .title("In-Store & Online Support")
                                .description("Visit us or order easily — responsive service.").build(),
                        FooterPromiseDTO.builder().icon("delivery")
                                .title("Nationwide Delivery")
                                .description("Smooth and reliable delivery across Bangladesh.").build(),
                        FooterPromiseDTO.builder().icon("globe")
                                .title("International Orders")
                                .description("WhatsApp: +880 1700-000000").build()))
                .outlets(List.of(
                        FooterOutletDTO.builder()
                                .imageUrl("https://images.unsplash.com/photo-1441986300917-64674bd600d8?auto=format&fit=crop&w=600&q=80")
                                .name("Flagship Store")
                                .address("Level 3, Example Market, Dhaka")
                                .phone("+880 1700-000000").build()))
                .copyright("© 2026 EREZER STORE — Handcrafted for Confidence.")
                .tagline("Secure payments • Nationwide shipping")
                .build();
    }

    private SizeChartRowDTO row(String size, double chestCm, double chestIn,
                                double lengthCm, double lengthIn) {
        return SizeChartRowDTO.builder()
                .size(size)
                .cells(List.of(
                        SizeChartCellDTO.builder().cm(BigDecimal.valueOf(chestCm)).inch(BigDecimal.valueOf(chestIn)).build(),
                        SizeChartCellDTO.builder().cm(BigDecimal.valueOf(lengthCm)).inch(BigDecimal.valueOf(lengthIn)).build()
                ))
                .build();
    }

    private String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise store-settings JSON; storing null. {}", e.getMessage());
            return null;
        }
    }

    private <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse stored {} JSON: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse stored list JSON: {}", e.getMessage());
            return null;
        }
    }

    private StoreSettingsDTO toDTO(StoreSettings s) {
        return StoreSettingsDTO.builder()
                .returnPolicyText(s.getReturnPolicyText())
                .exchangeWindowDays(s.getExchangeWindowDays())
                .supportPhone(s.getSupportPhone())
                .supportEmail(s.getSupportEmail())
                .supportHours(s.getSupportHours())
                .sizeChart(read(s.getSizeChartJson(), SizeChartDTO.class))
                .brandStory(read(s.getBrandStoryJson(), BrandStoryDTO.class))
                .footer(read(s.getFooterJson(), FooterDTO.class))
                .marquee(read(s.getMarqueeJson(), MarqueeDTO.class))
                .highlights(readList(s.getHighlightsJson(), new TypeReference<List<HighlightDTO>>() {}))
                // Null (legacy rows) → enabled, so existing checkouts keep every method.
                .paymentCodEnabled(s.getPaymentCodEnabled() == null || s.getPaymentCodEnabled())
                .paymentBkashEnabled(s.getPaymentBkashEnabled() == null || s.getPaymentBkashEnabled())
                .paymentCardEnabled(s.getPaymentCardEnabled() == null || s.getPaymentCardEnabled())
                .build();
    }
}
