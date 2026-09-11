package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.product.PriceChangeRequestDTO;
import kn.org.deliverybackend.dto.response.product.PriceChangePreviewDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.CategoryPriceChange.PriceMode;
import kn.org.deliverybackend.service.CategoryPriceChange.SaleMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The rules around a category price change: what is saved, and when nothing is. */
class PriceChangeServiceImplTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final VariantRepository variants = mock(VariantRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final PriceChangeServiceImpl service = new PriceChangeServiceImpl(products, variants, categories);

    private Product hoodie;
    private Product tee;
    private Variant xxl;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setId(7L);
        category.setName("Hoodies");
        when(categories.findById(7L)).thenReturn(Optional.of(category));

        hoodie = product(1L, "Classic Hoodie", "1400", "1260");
        tee = product(2L, "Plain Hoodie", "900", null);
        xxl = new Variant();
        xxl.setId(11L);
        xxl.setProductId(1L);
        xxl.setSize("XXL");
        xxl.setPriceOverride(new BigDecimal("1500"));
        when(products.findLiveByCategory(7L)).thenReturn(List.of(hoodie, tee));
        when(variants.findLiveByProductIds(anyCollection())).thenReturn(List.of(xxl));
    }

    @Test
    void thePreviewCoversEveryProductAndSavesNothing() {
        PriceChangePreviewDTO preview = service.preview(request(PriceMode.RAISE_PERCENT, "10", SaleMode.KEEP, null, null),
                null, 0, 20);

        assertEquals(2, preview.productCount());
        assertEquals(2, preview.changedCount());
        PriceChangePreviewDTO.Row first = preview.rows().get(0);
        assertAmount("1540", first.newPrice());
        assertAmount("1386", first.newSalePrice());
        assertAmount("1650", first.sizes().get(0).newPrice());
        assertAmount("1400", hoodie.getPrice());
        verify(products, never()).saveAll(any());
    }

    @Test
    void applySavesPricesSalesAndSizesOfTheTickedProductsOnly() {
        service.apply(request(PriceMode.RAISE_PERCENT, "10", SaleMode.KEEP, null, List.of(1L)));

        assertAmount("1540", hoodie.getPrice());
        assertAmount("1386", hoodie.getDiscountPrice());
        assertAmount("1650", xxl.getPriceOverride());
        assertAmount("900", tee.getPrice());
        verify(products).saveAll(List.of(hoodie));
    }

    @Test
    void nothingIsSavedWhenAnyPriceWouldDropToZero() {
        InvalidRequestException refused = assertThrows(InvalidRequestException.class,
                () -> service.apply(request(PriceMode.LOWER_AMOUNT, "900", SaleMode.KEEP, null, List.of(1L, 2L))));

        assertTrue(refused.getMessage().contains("Plain Hoodie"), refused.getMessage());
        assertAmount("1400", hoodie.getPrice());
        verify(products, never()).saveAll(any());
    }

    @Test
    void aTickedProductThatLeftTheCategoryStopsTheWholeChange() {
        assertThrows(InvalidRequestException.class,
                () -> service.apply(request(PriceMode.RAISE_AMOUNT, "100", SaleMode.KEEP, null, List.of(1L, 99L))));
        verify(products, never()).saveAll(any());
    }

    @Test
    void applyNeedsTickedProducts() {
        assertThrows(InvalidRequestException.class,
                () -> service.apply(request(PriceMode.RAISE_AMOUNT, "100", SaleMode.KEEP, null, List.of())));
    }

    @Test
    void settingASaleAloneLeavesPricesAndSizesAlone() {
        PriceChangePreviewDTO preview = service.preview(request(PriceMode.KEEP, null, SaleMode.SET, "20", null),
                null, 0, 20);

        PriceChangePreviewDTO.Row hoodieRow = preview.rows().get(0);
        assertAmount("1400", hoodieRow.newPrice());
        assertAmount("1120", hoodieRow.newSalePrice());
        assertAmount("1500", hoodieRow.sizes().get(0).newPrice());
        assertNotNull(preview.rows().get(1).newSalePrice(), "a product that wasn't on sale gets the sale too");
    }

    @Test
    void thePreviewPagesAndSearchesButCountsTheWholeCategory() {
        PriceChangeRequestDTO raise = request(PriceMode.RAISE_PERCENT, "10", SaleMode.KEEP, null, null);

        PriceChangePreviewDTO second = service.preview(raise, null, 1, 1);
        assertEquals(1, second.page());
        assertEquals(2, second.totalRows());
        assertEquals(2, second.totalPages());
        assertEquals(List.of(2L), second.rows().stream().map(PriceChangePreviewDTO.Row::productId).toList());
        assertEquals(2, second.productCount(), "the counts cover every page");
        assertEquals(List.of(1L, 2L), second.changeableIds(), "so do the products ticked by default");

        PriceChangePreviewDTO found = service.preview(raise, "  PLAIN ", 0, 20);
        assertEquals(List.of(2L), found.rows().stream().map(PriceChangePreviewDTO.Row::productId).toList());
        assertEquals(1, found.totalRows());
        assertEquals(2, found.productCount(), "the search narrows the rows, not the counts");

        assertEquals(1, service.preview(raise, null, 9, 1).page(), "a page past the end shows the last page");
    }

    @Test
    void aProductThatCantBeChangedIsNotTickedByDefault() {
        PriceChangePreviewDTO preview = service.preview(
                request(PriceMode.LOWER_AMOUNT, "900", SaleMode.KEEP, null, null), null, 0, 20);

        assertEquals(1, preview.problemCount());
        assertEquals(List.of(1L), preview.changeableIds());
    }

    private static Product product(long id, String name, String price, String sale) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        p.setDiscountPrice(sale == null ? null : new BigDecimal(sale));
        return p;
    }

    private static PriceChangeRequestDTO request(PriceMode priceMode, String priceValue, SaleMode saleMode,
                                                 String salePercent, List<Long> productIds) {
        return new PriceChangeRequestDTO(7L, priceMode, priceValue == null ? null : new BigDecimal(priceValue),
                saleMode, salePercent == null ? null : new BigDecimal(salePercent), productIds);
    }

    private static void assertAmount(String want, BigDecimal got) {
        assertEquals(0, new BigDecimal(want).compareTo(got), () -> "expected " + want + ", got " + got);
    }
}
