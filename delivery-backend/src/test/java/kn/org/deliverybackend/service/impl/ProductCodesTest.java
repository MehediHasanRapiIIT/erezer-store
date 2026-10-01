package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Suggested product codes: the category's initials and the next free number. */
class ProductCodesTest {

    // ── the letters ────────────────────────────────────────────────────────────

    @Test
    void twoWordsGiveTheirInitials() {
        assertEquals("EP", ProductCodes.prefixFor("Erezer Pink"));
        assertEquals("TS", ProductCodes.prefixFor("T-Shirts"));
    }

    @Test
    void oneWordGivesItsFirstTwoLetters() {
        assertEquals("CA", ProductCodes.prefixFor("Caps"));
        assertEquals("AC", ProductCodes.prefixFor("Accessories"));
        assertEquals("NE", ProductCodes.prefixFor("New"));
    }

    @Test
    void aNameWithNoEnglishLettersFallsBackToPR() {
        assertEquals("PR", ProductCodes.prefixFor("শাড়ি"));
        assertEquals("PR", ProductCodes.prefixFor(""));
        assertEquals("PR", ProductCodes.prefixFor(null));
        assertEquals("PR", ProductCodes.prefixFor("X"));
    }

    // ── the number ─────────────────────────────────────────────────────────────

    @Test
    void numbersCarryOnAfterTheHighest() {
        assertEquals(1003, ProductCodes.nextNumber("EP", List.of("EP-1002", "EP-1002", "EP-998")));
    }

    @Test
    void aCategoryWithNoCodesYetStartsAt1001() {
        assertEquals(1001, ProductCodes.nextNumber("CA", List.of()));
    }

    @Test
    void onlyCodesInExactlyThisStyleCount() {
        assertEquals(1001, ProductCodes.nextNumber("EP",
                List.of("EP-1002-B", "EPX-9999", "EP-", "EP-12a", "XEP-5000")));
    }

    @Test
    void capitalsDontMatter() {
        assertEquals(1006, ProductCodes.nextNumber("EP", List.of("ep-1005")));
    }

    // ── the whole suggestion ───────────────────────────────────────────────────

    @Test
    void severalCodesInARow() {
        CategoryRepository categories = mock(CategoryRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        Category pink = new Category();
        pink.setId(4L);
        pink.setName("Erezer Pink");
        when(categories.findById(4L)).thenReturn(Optional.of(pink));
        when(products.findCodesStartingWith("EP")).thenReturn(List.of("EP-1002"));

        assertEquals(List.of("EP-1003", "EP-1004", "EP-1005"),
                new ProductCodes(categories, products).next(4L, 3));
    }

    @Test
    void askingForNoneOrTooManyIsRefused() {
        ProductCodes codes = new ProductCodes(mock(CategoryRepository.class), mock(ProductRepository.class));
        assertThrows(InvalidRequestException.class, () -> codes.next(4L, 0));
        assertThrows(InvalidRequestException.class, () -> codes.next(4L, 51));
    }
}
