package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The storefront's sitemap for search engines: the standing pages, every live
 * category and every live product, built from the catalog on each request so a
 * new product is listed without anyone doing anything.
 *
 * <p>The storefront serves this at {@code /sitemap.xml} (its nginx passes that
 * one address through to here), which is where robots.txt points.
 *
 * <p>Left out: deleted products and categories, switched-off categories, the
 * subcategories of a switched-off category, and products that sit in any of
 * those. Out-of-stock products stay in; their page still opens.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Storefront: Sitemap")
public class SitemapController {

    /** Standing pages worth finding in a search: path, how often it changes, weight. */
    private static final String[][] PAGES = {
            {"/", "daily", "1.0"},
            {"/shop", "daily", "0.9"},
            {"/categories", "weekly", "0.8"},
            {"/custom-design", "monthly", "0.8"},
            {"/flash-sale", "daily", "0.6"},
            {"/bundles", "weekly", "0.6"},
            {"/about", "monthly", "0.4"},
            {"/contact", "monthly", "0.4"},
    };

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final kn.org.deliverybackend.repository.ContentPageRepository contentPageRepository;

    @Value("${app.frontend.store-url}")
    private String storeUrl;

    @GetMapping(value = "/sitemap.xml", produces = "application/xml")
    public ResponseEntity<byte[]> sitemap() {
        String base = storeUrl.endsWith("/") ? storeUrl.substring(0, storeUrl.length() - 1) : storeUrl;

        Map<Long, Category> byId = new HashMap<>();
        List<Category> categories = categoryRepository.findAll();
        for (Category c : categories) {
            if (c.getId() != null) byId.put(c.getId(), c);
        }

        StringBuilder xml = new StringBuilder(8192);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String[] page : PAGES) {
            url(xml, base + page[0], null, page[1], page[2]);
        }
        // The shop's own pages: Our Mission, Our Values, and any other it has written.
        for (var page : contentPageRepository.findLive()) {
            if (!Boolean.TRUE.equals(page.getIsActive())) continue;
            url(xml, base + "/pages/" + page.getSlug(), page.getUpdatedAt(), "monthly", "0.5");
        }
        for (Category c : categories) {
            if (!shown(c, byId) || c.getSlug() == null || c.getSlug().isBlank()) continue;
            url(xml, base + "/" + c.getSlug().trim(), c.getUpdatedAt(), "daily", "0.8");
        }
        for (Product p : productRepository.findAll()) {
            if (Boolean.TRUE.equals(p.getDeleted()) || p.getId() == null || p.getPrice() == null) continue;
            // A product with no category is still sold; one in a hidden category is not shown.
            if (p.getCategoryId() != null && !shown(byId.get(p.getCategoryId()), byId)) continue;
            url(xml, base + "/product/" + p.getId(), p.getUpdatedAt(), "weekly", "0.7");
        }
        xml.append("</urlset>\n");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/xml; charset=UTF-8")
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(xml.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Live and switched on, and so is every category it sits under. */
    private static boolean shown(Category c, Map<Long, Category> byId) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Category at = c; ; at = byId.get(at.getParentId())) {
            if (at == null || Boolean.TRUE.equals(at.getDeleted()) || Boolean.FALSE.equals(at.getIsActive())) return false;
            if (at.getParentId() == null || !seen.add(at.getId())) return true;
        }
    }

    private static void url(StringBuilder xml, String loc, Date changed, String frequency, String priority) {
        xml.append("  <url>\n    <loc>").append(escape(loc)).append("</loc>\n");
        if (changed != null) {
            xml.append("    <lastmod>").append(DAY.format(changed.toInstant())).append("</lastmod>\n");
        }
        xml.append("    <changefreq>").append(frequency).append("</changefreq>\n");
        xml.append("    <priority>").append(priority).append("</priority>\n  </url>\n");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
