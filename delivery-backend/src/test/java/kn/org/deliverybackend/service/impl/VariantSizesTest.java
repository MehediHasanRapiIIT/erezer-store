package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import kn.org.deliverybackend.dto.variant.VariantResponseDTO;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sizes can be added one at a time or several at once, and never twice. */
class VariantSizesTest {

    private final VariantRepository variants = mock(VariantRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final VariantServiceImpl service = new VariantServiceImpl(variants, products);

    /** What the product already has. */
    private final List<Variant> onTheProduct = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Product p = new Product();
        p.setId(7L);
        p.setSku("ER-00007");
        when(products.findById(7L)).thenReturn(Optional.of(p));
        when(variants.findByProductId(7L)).thenReturn(onTheProduct);
        when(variants.save(any(Variant.class))).thenAnswer(inv -> {
            Variant v = inv.getArgument(0);
            if (v.getId() == null) v.setId(100L + onTheProduct.size());
            return v;
        });
    }

    private static VariantRequestDTO size(String size, int stock) {
        VariantRequestDTO r = new VariantRequestDTO();
        r.setSize(size);
        r.setStockQuantity(stock);
        return r;
    }

    private void productAlreadyHas(String size) {
        Variant v = new Variant();
        v.setId(50L + onTheProduct.size());
        v.setProductId(7L);
        v.setSize(size);
        onTheProduct.add(v);
    }

    @Test
    void severalSizesAtOnce() {
        List<VariantResponseDTO> made = service.createAll(7L,
                List.of(size("S", 5), size("M", 8), size("L", 3)));
        assertEquals(List.of("S", "M", "L"), made.stream().map(VariantResponseDTO::getSize).toList());
        assertEquals(List.of(5, 8, 3), made.stream().map(VariantResponseDTO::getStockQuantity).toList());
    }

    @Test
    void aSizeListedTwiceStopsTheLotBeforeAnythingIsSaved() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> service.createAll(7L, List.of(size("S", 1), size("m", 1), size("M", 1))));
        assertTrue(e.getMessage().contains("listed twice"), e.getMessage());
        verify(variants, never()).save(any());
    }

    @Test
    void aSizeTheProductAlreadyHasStopsTheLot() {
        productAlreadyHas("M");
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> service.createAll(7L, List.of(size("S", 1), size("M", 1))));
        assertTrue(e.getMessage().contains("already has size M"), e.getMessage());
        verify(variants, never()).save(any());
    }

    @Test
    void everyRowNeedsASize() {
        assertThrows(InvalidRequestException.class,
                () -> service.createAll(7L, List.of(size("S", 1), size("  ", 1))));
        verify(variants, never()).save(any());
    }

    @Test
    void addingOneSizeTheProductAlreadyHasIsRefusedToo() {
        productAlreadyHas("L");
        assertThrows(InvalidRequestException.class, () -> service.create(7L, size("l", 2)));
    }

    @Test
    void changingASizeToOneAlreadyTakenIsRefused() {
        productAlreadyHas("S");
        productAlreadyHas("M");
        Variant small = onTheProduct.get(0);
        when(variants.findById(small.getId())).thenReturn(Optional.of(small));
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> service.update(7L, small.getId(), size("M", 1)));
        assertTrue(e.getMessage().contains("already has size M"));
    }

    @Test
    void keepingTheSameSizeWhileChangingTheStockIsFine() {
        productAlreadyHas("S");
        Variant small = onTheProduct.get(0);
        when(variants.findById(small.getId())).thenReturn(Optional.of(small));
        VariantResponseDTO saved = service.update(7L, small.getId(), size("S", 12));
        assertEquals(12, saved.getStockQuantity());
    }
}
