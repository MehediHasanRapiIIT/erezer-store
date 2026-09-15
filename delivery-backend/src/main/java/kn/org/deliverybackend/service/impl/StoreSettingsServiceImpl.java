package kn.org.deliverybackend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kn.org.deliverybackend.dto.settings.AboutPageDTO;
import kn.org.deliverybackend.dto.settings.AboutSectionDTO;
import kn.org.deliverybackend.exception.InvalidRequestException;
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
import kn.org.deliverybackend.dto.coupon.CouponSwitchDTO;
import kn.org.deliverybackend.dto.discount.DiscountSwitchesDTO;
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
        if (settings.getAboutPageJson() == null || settings.getAboutPageJson().isBlank()) {
            settings.setAboutPageJson(write(defaultAboutPage()));
            changed = true;
        }
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
        // An older admin panel that doesn't know the About page must not wipe it.
        if (request.getAboutPage() != null) {
            settings.setAboutPageJson(write(checkedAboutPage(request.getAboutPage())));
        }
        settings.setFooterJson(write(request.getFooter()));
        settings.setMarqueeJson(write(request.getMarquee()));
        settings.setHighlightsJson(write(request.getHighlights()));
        // Payment toggles: null in request → default enabled.
        settings.setPaymentCodEnabled(request.getPaymentCodEnabled() == null || request.getPaymentCodEnabled());
        settings.setPaymentBkashEnabled(request.getPaymentBkashEnabled() == null || request.getPaymentBkashEnabled());
        settings.setPaymentCardEnabled(request.getPaymentCardEnabled() == null || request.getPaymentCardEnabled());
        // The discount switches and the promo code switch are not saved here:
        // they have their own endpoints and permissions, so saving the settings
        // page can never turn discounting or promo codes on or off.

        return toDTO(repository.save(settings));
    }

    @Override
    @Transactional
    public DiscountSwitchesDTO getDiscountSwitches() {
        return DiscountSwitchesDTO.from(get());
    }

    @Override
    @Transactional
    public DiscountSwitchesDTO updateDiscountSwitches(DiscountSwitchesDTO change) {
        StoreSettings settings = repository.findById(StoreSettings.SINGLETON_ID)
                .orElseGet(this::seedDefaults);
        if (change.discountsEnabled() != null) settings.setDiscountsEnabled(change.discountsEnabled());
        if (change.discountsGlobalEnabled() != null) settings.setDiscountsGlobalEnabled(change.discountsGlobalEnabled());
        if (change.discountsCategoryEnabled() != null) settings.setDiscountsCategoryEnabled(change.discountsCategoryEnabled());
        if (change.discountsProductEnabled() != null) settings.setDiscountsProductEnabled(change.discountsProductEnabled());
        return DiscountSwitchesDTO.from(toDTO(repository.save(settings)));
    }

    @Override
    @Transactional
    public CouponSwitchDTO getCouponSwitch() {
        return new CouponSwitchDTO(get().getCouponsEnabled());
    }

    @Override
    @Transactional
    public CouponSwitchDTO updateCouponSwitch(CouponSwitchDTO change) {
        StoreSettings settings = repository.findById(StoreSettings.SINGLETON_ID)
                .orElseGet(this::seedDefaults);
        if (change.couponsEnabled() != null) settings.setCouponsEnabled(change.couponsEnabled());
        return new CouponSwitchDTO(toDTO(repository.save(settings)).getCouponsEnabled());
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
                .aboutPageJson(write(defaultAboutPage()))
                .footerJson(write(defaultFooter()))
                .marqueeJson(write(defaultMarquee()))
                .highlightsJson(write(defaultHighlights()))
                .paymentCodEnabled(true)
                .paymentBkashEnabled(true)
                .paymentCardEnabled(true)
                .discountsEnabled(true)
                .discountsGlobalEnabled(true)
                .discountsCategoryEnabled(true)
                .discountsProductEnabled(true)
                .couponsEnabled(true)
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

    /** Longest lengths the About page accepts, so one paste can't bloat every page load. */
    private static final int ABOUT_TITLE_MAX = 150;
    private static final int ABOUT_INTRO_MAX = 1_000;
    private static final int ABOUT_BODY_MAX = 5_000;
    private static final int ABOUT_SECTIONS_MAX = 12;

    /**
     * Trims the About page and refuses what the storefront should never render:
     * over-long text, too many sections, or a link or image that is not a web
     * address or a page of this shop (a "javascript:" link, for instance).
     */
    private AboutPageDTO checkedAboutPage(AboutPageDTO page) {
        List<AboutSectionDTO> sections = page.getSections() == null ? List.of() : page.getSections().stream()
                .filter(s -> s != null && (notBlank(s.getHeading()) || notBlank(s.getBody()) || notBlank(s.getImageUrl())))
                .map(s -> AboutSectionDTO.builder()
                        .heading(limited(s.getHeading(), ABOUT_TITLE_MAX, "A section heading"))
                        .body(limited(s.getBody(), ABOUT_BODY_MAX, "A section's text"))
                        .imageUrl(webAddress(s.getImageUrl(), false, "A section photo"))
                        .build())
                .toList();
        if (sections.size() > ABOUT_SECTIONS_MAX) {
            throw new InvalidRequestException("The About page can have at most " + ABOUT_SECTIONS_MAX + " sections.");
        }
        return AboutPageDTO.builder()
                .title(limited(page.getTitle(), ABOUT_TITLE_MAX, "The title"))
                .intro(limited(page.getIntro(), ABOUT_INTRO_MAX, "The intro"))
                .heroImageUrl(webAddress(page.getHeroImageUrl(), false, "The main photo"))
                .sections(sections)
                .ctaLabel(limited(page.getCtaLabel(), ABOUT_TITLE_MAX, "The button label"))
                .ctaLink(webAddress(page.getCtaLink(), true, "The button link"))
                .build();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String limited(String text, int max, String what) {
        if (text == null || text.isBlank()) return null;
        String t = text.trim();
        if (t.length() > max) {
            throw new InvalidRequestException(what + " is too long (at most " + max + " characters).");
        }
        return t;
    }

    /** An http(s) address, or (for links) a page of this shop such as "/shop". */
    private static String webAddress(String value, boolean allowShopPage, String what) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        String lower = v.toLowerCase(java.util.Locale.ROOT);
        boolean web = lower.startsWith("https://") || lower.startsWith("http://");
        boolean shopPage = allowShopPage && v.startsWith("/") && !v.startsWith("//");
        if (!web && !shopPage || v.length() > 2_000) {
            throw new InvalidRequestException(what + " must be a web address (https://…)"
                    + (allowShopPage ? " or a page of the shop, like /shop." : "."));
        }
        return v;
    }

    private AboutPageDTO defaultAboutPage() {
        return AboutPageDTO.builder()
                .title("About Erezer")
                .intro("Erezer is an apparel brand from Dhaka, making considered everyday clothing "
                        + "that is designed to be worn for years, not seasons.")
                .heroImageUrl("https://images.unsplash.com/photo-1490481651871-ab68de25d43d?auto=format&fit=crop&w=1600&q=80")
                .sections(List.of(
                        AboutSectionDTO.builder()
                                .heading("Our story")
                                .body("We started Erezer with a simple idea: clothing should feel good, look good "
                                        + "and last. Every piece begins with fabric we would wear ourselves.")
                                .imageUrl("https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?auto=format&fit=crop&w=900&q=80")
                                .build(),
                        AboutSectionDTO.builder()
                                .heading("Made with care")
                                .body("We work closely with our makers, keep our collections small, and price "
                                        + "honestly - so you pay for quality, not for marketing.")
                                .imageUrl("https://images.unsplash.com/photo-1539109136881-3be0616acf4b?auto=format&fit=crop&w=900&q=80")
                                .build(),
                        AboutSectionDTO.builder()
                                .heading("Visit us")
                                .body("Come and see the collection in person at our flagship store, "
                                        + "or reach our team any time through the Contact page.")
                                .build()))
                .ctaLabel("Shop the collection")
                .ctaLink("/shop")
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
                .aboutPage(read(s.getAboutPageJson(), AboutPageDTO.class))
                .footer(read(s.getFooterJson(), FooterDTO.class))
                .marquee(read(s.getMarqueeJson(), MarqueeDTO.class))
                .highlights(readList(s.getHighlightsJson(), new TypeReference<List<HighlightDTO>>() {}))
                // Null (legacy rows) → enabled, so existing checkouts keep every method.
                .paymentCodEnabled(s.getPaymentCodEnabled() == null || s.getPaymentCodEnabled())
                .paymentBkashEnabled(s.getPaymentBkashEnabled() == null || s.getPaymentBkashEnabled())
                .paymentCardEnabled(s.getPaymentCardEnabled() == null || s.getPaymentCardEnabled())
                // Same rule for the discount switches: null → discounting is on.
                .discountsEnabled(s.getDiscountsEnabled() == null || s.getDiscountsEnabled())
                .discountsGlobalEnabled(s.getDiscountsGlobalEnabled() == null || s.getDiscountsGlobalEnabled())
                .discountsCategoryEnabled(s.getDiscountsCategoryEnabled() == null || s.getDiscountsCategoryEnabled())
                .discountsProductEnabled(s.getDiscountsProductEnabled() == null || s.getDiscountsProductEnabled())
                .couponsEnabled(s.getCouponsEnabled() == null || s.getCouponsEnabled())
                // Shipping rules: null means off, so shipping is charged.
                .shippingFreeAll(Boolean.TRUE.equals(s.getShippingFreeAll()))
                .shippingOfferEnabled(Boolean.TRUE.equals(s.getShippingOfferEnabled()))
                .shippingOfferMin(s.getShippingOfferMin())
                .build();
    }
}
