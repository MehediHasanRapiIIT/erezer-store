package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.product.CodeChangeRequestDTO;
import kn.org.deliverybackend.dto.response.product.CodeChangePreviewDTO;
import kn.org.deliverybackend.dto.response.product.CodeChangePreviewDTO.Row;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.service.CategoryCodeChange.CodeMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Giving a category its codes: the same one for all, or numbers in name order. */
class CodeChangeServiceImplTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final CodeChangeServiceImpl service = new CodeChangeServiceImpl(products, categories);

    private Product anorak;
    private Product bomber;
    private Product cape;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setId(7L);
        category.setName("Jackets");
        when(categories.findById(7L)).thenReturn(Optional.of(category));
        // findLiveByCategory returns them in name order.
        anorak = product(1L, "Anorak", "PI-00001");
        bomber = product(2L, "Bomber", "PI-00002");
        cape = product(3L, "Cape", "PI-00003");
        when(products.findLiveByCategory(7L)).thenReturn(List.of(anorak, bomber, cape));
    }

    // ── the same code for every product ──────────────────────────────────────

    @Test
    void everyTickedProductGetsTheSameCode() {
        CodeChangePreviewDTO preview = service.preview(same("EP-1001", null), null, 0, 20);

        assertEquals(List.of("EP-1001", "EP-1001", "EP-1001"), codes(preview));
        assertEquals(3, preview.changedCount());
        assertEquals("PI-00001", anorak.getProductCode(), "a preview saves nothing");
        verify(products, never()).saveAll(any());
    }

    @Test
    void applyGivesTheTickedProductsThatCodeAndLeavesTheRest() {
        service.apply(same("EP-1001", List.of(2L)));

        assertEquals("EP-1001", anorak.getProductCode());
        assertEquals("PI-00002", bomber.getProductCode(), "left alone");
        assertEquals("EP-1001", cape.getProductCode());
        verify(products).saveAll(List.of(anorak, cape));
    }

    @Test
    void theSameCodeAppliedTwiceChangesNothingTheSecondTime() {
        service.apply(same("EP-1001", null));
        InvalidRequestException refused = assertThrows(InvalidRequestException.class,
                () -> service.apply(same("EP-1001", null)));
        assertEquals("Nothing to change: every product is unticked or already has this code.", refused.getMessage());
    }

    @Test
    void spacesAroundTheCodeAreDropped() {
        service.apply(same("  EP-1001  ", null));
        assertEquals("EP-1001", anorak.getProductCode());
    }

    // ── a prefix with numbers ────────────────────────────────────────────────

    @Test
    void numbersRunInNameOrderFrom001() {
        CodeChangePreviewDTO preview = service.preview(numbered("JK", null), null, 0, 20);

        assertEquals(List.of("JK-001", "JK-002", "JK-003"), codes(preview));
        assertEquals(3, preview.changedCount());
    }

    @Test
    void anUntickedProductKeepsItsCodeAndItsNumberIsSkipped() {
        // Bomber is left alone and already holds JK-001, so nobody else gets that number.
        bomber.setProductCode("JK-001");
        CodeChangePreviewDTO preview = service.preview(numbered("JK", List.of(2L)), null, 0, 20);

        assertEquals(List.of("JK-002", "JK-001", "JK-003"), codes(preview));
        assertEquals(2, preview.changedCount(), "only the ticked two change");
    }

    @Test
    void numberingAppliedTwiceChangesNothingTheSecondTime() {
        service.apply(numbered("JK", null));
        assertEquals(List.of("JK-001", "JK-002", "JK-003"),
                List.of(anorak.getProductCode(), bomber.getProductCode(), cape.getProductCode()));

        assertThrows(InvalidRequestException.class, () -> service.apply(numbered("JK", null)));
    }

    // ── pages, search and refusals ───────────────────────────────────────────

    @Test
    void thePreviewPagesAndSearchesButCountsTheWholeCategory() {
        CodeChangePreviewDTO second = service.preview(numbered("JK", null), null, 1, 2);
        assertEquals(List.of("JK-003"), codes(second));
        assertEquals(3, second.productCount());
        assertEquals(2, second.totalPages());

        CodeChangePreviewDTO found = service.preview(numbered("JK", null), "bomb", 0, 20);
        assertEquals(List.of("JK-002"), codes(found));
        assertEquals(3, found.productCount(), "the search narrows the rows, not the counts");

        CodeChangePreviewDTO byNewCode = service.preview(numbered("JK", null), "jk-003", 0, 20);
        assertEquals(List.of("JK-003"), codes(byNewCode), "the new code can be searched too");
    }

    @Test
    void whatCantBeUsedIsRefused() {
        assertThrows(InvalidRequestException.class, () -> service.preview(same("  ", null), null, 0, 20));
        assertThrows(InvalidRequestException.class, () -> service.apply(numbered("JK 1", null)));
        assertThrows(InvalidRequestException.class, () -> service.apply(same("X".repeat(41), null)));
        verify(products, never()).saveAll(any());
    }

    private static List<String> codes(CodeChangePreviewDTO preview) {
        return preview.rows().stream().map(Row::newCode).toList();
    }

    private static Product product(long id, String name, String code) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setProductCode(code);
        return p;
    }

    private static CodeChangeRequestDTO same(String code, List<Long> excluded) {
        return new CodeChangeRequestDTO(7L, CodeMode.SAME, code, excluded);
    }

    private static CodeChangeRequestDTO numbered(String prefix, List<Long> excluded) {
        return new CodeChangeRequestDTO(7L, CodeMode.NUMBERED, prefix, excluded);
    }
}
