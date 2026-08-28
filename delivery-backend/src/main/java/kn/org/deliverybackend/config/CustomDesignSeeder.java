package kn.org.deliverybackend.config;

import kn.org.deliverybackend.entity.CustomDesignColor;
import kn.org.deliverybackend.entity.CustomDesignItem;
import kn.org.deliverybackend.repository.CustomDesignItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dev-only seeder: inserts a couple of sample garments so the custom-design
 * studio isn't empty on a fresh local database. Runs only under the {@code local}
 * profile and only when no garments exist yet, so it never touches real data.
 *
 * <p>The sample colours carry no mockup image URLs (there are none in MinIO on a
 * fresh box), so the studio shows a plain white canvas for them until an admin
 * uploads real front/back/sleeve mockups via the Custom Design screen.
 */
@Component
@Profile("local")
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class CustomDesignSeeder implements ApplicationRunner {

    private final CustomDesignItemRepository itemRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (itemRepository.count() > 0) {
            return;
        }
        itemRepository.save(tShirt());
        itemRepository.save(hoodie());
        log.info("Seeded 2 sample custom-design garment(s) for local dev.");
    }

    private CustomDesignItem tShirt() {
        CustomDesignItem item = CustomDesignItem.builder()
                .name("T-Shirt")
                .category("Unisex")
                .sortOrder(0)
                .active(true)
                .sizes(List.of("S", "M", "L", "XL", "XXL"))
                .printTechniques(List.of("Screen Print", "Embroidery", "DTG"))
                .build();
        item.addColor(color("White", "#FFFFFF", 0));
        item.addColor(color("Black", "#111111", 1));
        item.addColor(color("Red", "#CC0F0F", 2));
        item.addColor(color("Navy", "#1F2A44", 3));
        return item;
    }

    private CustomDesignItem hoodie() {
        CustomDesignItem item = CustomDesignItem.builder()
                .name("Hoodie")
                .category("Unisex")
                .sortOrder(1)
                .active(true)
                .sizes(List.of("M", "L", "XL"))
                .printTechniques(List.of("Screen Print", "Embroidery"))
                .build();
        item.addColor(color("Black", "#111111", 0));
        item.addColor(color("Grey", "#9CA3AF", 1));
        return item;
    }

    private CustomDesignColor color(String name, String hex, int sortOrder) {
        return CustomDesignColor.builder()
                .name(name)
                .hex(hex)
                .sortOrder(sortOrder)
                .build();
    }
}
