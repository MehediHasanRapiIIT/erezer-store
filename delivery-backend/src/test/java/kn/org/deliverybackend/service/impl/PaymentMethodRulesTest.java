package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.settings.StoreSettingsDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.service.StoreSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Ways to pay per product and category: the product wins, then its category, then the one above; the shop's switch first. */
class PaymentMethodRulesTest {

    private final StoreSettingsService settings = mock(StoreSettingsService.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final PaymentMethodRules rules = new PaymentMethodRules(settings, categories);
    private final Map<Long, Category> stored = new HashMap<>();
    private StoreSettingsDTO shop;

    @BeforeEach
    void setUp() {
        shop = StoreSettingsDTO.builder().paymentCodEnabled(true).paymentBkashEnabled(true).paymentCardEnabled(false).build();
        when(settings.get()).thenAnswer(inv -> shop);
        when(categories.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(stored.get((Long) inv.getArgument(0))));
    }

    private Category category(long id, Long parent) {
        Category c = new Category();
        c.setId(id);
        c.setName("Category " + id);
        c.setParentId(parent);
        stored.put(id, c);
        return c;
    }

    private static Product product(String name, Long categoryId) {
        Product p = new Product();
        p.setName(name);
        p.setCategoryId(categoryId);
        return p;
    }

    @Test
    void nothingSetMeansTheShopsSwitches() {
        var basket = rules.forBasket(List.of(product("Shirt", null)));
        assertEquals(List.of("CASH", "BKASH"), basket.allowed());
        assertNull(basket.message());
    }

    @Test
    void aProductCanRequireBkash() {
        Product custom = product("Custom shirt", null);
        custom.setPayCod(false);
        assertEquals(List.of("BKASH"), rules.forBasket(List.of(custom)).allowed());
        InvalidRequestException refused = assertThrows(InvalidRequestException.class, () -> rules.check("CASH", List.of(custom)));
        assertTrue(refused.getMessage().contains("Custom shirt can't be paid by Cash on delivery"), refused.getMessage());
        assertDoesNotThrow(() -> rules.check("BKASH", List.of(custom)));
    }

    @Test
    void theProductWinsOverItsCategory() {
        category(1, null).setPayBkash(false);
        Product shirt = product("Shirt", 1L);
        assertEquals(List.of("CASH"), rules.forBasket(List.of(shirt)).allowed());
        shirt.setPayBkash(true);
        assertEquals(List.of("CASH", "BKASH"), rules.forBasket(List.of(shirt)).allowed());
    }

    @Test
    void aSubcategoryFollowsTheOneAbove() {
        category(1, null).setPayCod(false);
        category(2, 1L);
        assertEquals(List.of("BKASH"), rules.forBasket(List.of(product("Saree", 2L))).allowed());
        stored.get(2L).setPayCod(true);
        assertEquals(List.of("CASH", "BKASH"), rules.forBasket(List.of(product("Saree", 2L))).allowed());
    }

    @Test
    void theShopSwitchComesFirst() {
        shop.setPaymentBkashEnabled(false);
        Product p = product("Shirt", null);
        p.setPayBkash(true);
        assertEquals(List.of("CASH"), rules.forBasket(List.of(p)).allowed());
        assertThrows(InvalidRequestException.class, () -> rules.check("BKASH", List.of(p)));
    }

    @Test
    void itemsThatCantShareAWayToPaySaySo() {
        Product codOnly = product("Classic T-shirt", null);
        codOnly.setPayBkash(false);
        Product bkashOnly = product("Custom shirt", null);
        bkashOnly.setPayCod(false);
        var basket = rules.forBasket(List.of(codOnly, bkashOnly));
        assertTrue(basket.allowed().isEmpty());
        assertTrue(basket.message().contains("Classic T-shirt: Cash on delivery only")
                && basket.message().contains("Custom shirt: bKash only")
                && basket.message().contains("order them separately"), basket.message());
        assertThrows(InvalidRequestException.class, () -> rules.check("CASH", List.of(codOnly, bkashOnly)));
    }

    @Test
    void blockedListsWhatTheProductOrCategoryRefuses() {
        category(1, null).setPayCod(false);
        Product p = product("Shirt", 1L);
        p.setPayCard(false);
        assertEquals(List.of("CASH", "CARD"), rules.blockedFor(p));
    }
}
