package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Product feed for Meta Commerce Manager, in the CSV layout Meta's catalog
 * import expects. Point a scheduled catalog feed at this URL and Meta pulls
 * it daily, which is what lets ads show a shopper the exact product they
 * looked at.
 *
 * <p>The {@code id} column is the numeric product id, the same value the
 * storefront pixel and the Conversions API put in {@code content_ids}. That
 * match is what makes catalog ads work.
 */
@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
@Tag(name = "Meta: Catalog feed")
public class MetaCatalogController {

    private static final String[] COLUMNS = {
            "id", "title", "description", "availability", "condition", "price", "sale_price",
            "link", "image_link", "brand", "product_type", "gender", "google_product_category",
    };

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Value("${app.frontend.store-url}")
    private String storeUrl;

    @GetMapping(value = "/catalog.csv", produces = "text/csv")
    public ResponseEntity<byte[]> catalog() {
        Map<Long, String> categoryNames = new HashMap<>();
        for (Category c : categoryRepository.findAll()) {
            if (c.getId() != null) categoryNames.put(c.getId(), c.getName());
        }

        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", COLUMNS)).append("\r\n");
        for (Product p : productRepository.findAll()) {
            if (Boolean.TRUE.equals(p.getDeleted()) || p.getPrice() == null) continue;
            csv.append(row(p, categoryNames.get(p.getCategoryId()))).append("\r\n");
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"erezer-catalog.csv\"")
                .body(bytes);
    }

    private String row(Product p, String categoryName) {
        boolean sellable = !Boolean.FALSE.equals(p.getIsAvailable()) && p.getStockQuantity() > 0;
        String base = storeUrl.endsWith("/") ? storeUrl.substring(0, storeUrl.length() - 1) : storeUrl;
        BigDecimal sale = p.getDiscountPrice();
        boolean onSale = sale != null && sale.signum() > 0 && sale.compareTo(p.getPrice()) < 0;

        List<String> cells = List.of(
                String.valueOf(p.getId()),
                nz(p.getName()),
                oneLine(nz(p.getDescription()).isEmpty() ? nz(p.getName()) : p.getDescription()),
                sellable ? "in stock" : "out of stock",
                "new",
                taka(p.getPrice()),
                onSale ? taka(sale) : "",
                base + "/product/" + p.getId(),
                nz(p.getImageUrl()),
                nz(p.getBrand()).isEmpty() ? "Erezer" : p.getBrand(),
                nz(categoryName),
                gender(p.getGender()),
                "Apparel & Accessories");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) line.append(',');
            line.append(quote(cells.get(i)));
        }
        return line.toString();
    }

    /** Meta wants "1200.00 BDT". */
    private static String taka(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " BDT";
    }

    /** Meta accepts only male / female / unisex; anything else is left blank. */
    private static String gender(String raw) {
        if (raw == null) return "";
        String g = raw.trim().toLowerCase(Locale.ROOT);
        return switch (g) {
            case "male", "men", "man" -> "male";
            case "female", "women", "woman" -> "female";
            case "unisex" -> "unisex";
            default -> "";
        };
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String nz(String value) {
        return value == null ? "" : value.trim();
    }

    private static String quote(String cell) {
        return "\"" + cell.replace("\"", "\"\"") + "\"";
    }
}
