package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.product.ProductRequestDTO;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.service.ProductCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Add several products": every row is checked before any is saved, a mistake
 * names its row, and the rows are saved in order through the one-product save
 * (which joins the same transaction, so the batch is all or nothing).
 */
class ProductBatchTest {

    private final ProductCreationService creation = mock(ProductCreationService.class);
    private final ProductBatchService batch = new ProductBatchService(creation);

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0};

    private static MultipartFile picture() {
        return new MockMultipartFile("pictures-0", "p.png", "image/png", PNG);
    }

    private static MultipartFile notAPicture() {
        return new MockMultipartFile("pictures-0", "notes.png", "image/png", "plain words".getBytes());
    }

    private static ProductRequestDTO product(String name, String price) {
        ProductRequestDTO p = new ProductRequestDTO();
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        return p;
    }

    private static VariantRequestDTO size(String s) {
        VariantRequestDTO r = new VariantRequestDTO();
        r.setSize(s);
        r.setStockQuantity(3);
        return r;
    }

    @Test
    void savesEveryRowInOrderWithItsOwnPictures() {
        when(creation.createWithEverything(any(), anyList(), anyList())).thenAnswer(inv -> {
            ProductResponseDTO r = new ProductResponseDTO();
            r.setName(((ProductRequestDTO) inv.getArgument(0)).getName());
            return r;
        });
        List<MultipartFile> first = List.of(picture(), picture(), picture(), picture());
        List<MultipartFile> second = List.of(picture(), picture());

        List<ProductResponseDTO> made = batch.createAll(
                List.of(product("Pink Floral", "1200"), product("Mauve Wrap", "1350")),
                List.of(size("S"), size("M")),
                List.of(first, second));

        assertEquals(List.of("Pink Floral", "Mauve Wrap"), made.stream().map(ProductResponseDTO::getName).toList());
        verify(creation).createWithEverything(any(), any(), org.mockito.ArgumentMatchers.eq(first));
        verify(creation).createWithEverything(any(), any(), org.mockito.ArgumentMatchers.eq(second));
    }

    @Test
    void aBadPictureInTheLastRowStopsTheBatchBeforeAnythingIsSaved() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> batch.createAll(
                List.of(product("One", "100"), product("Two", "100"), product("Three", "100")),
                List.of(),
                List.of(List.of(picture()), List.of(picture()), List.of(picture(), notAPicture()))));
        assertTrue(e.getMessage().startsWith("Row 3: Picture 2"), e.getMessage());
        verify(creation, never()).createWithEverything(any(), any(), any());
    }

    @Test
    void aDiscountThatDoesntFitOneRowsPriceNamesThatRow() {
        ProductRequestDTO cheap = product("Cheap", "100");
        cheap.setDiscountAmount(new BigDecimal("150"));   // the shared ৳150 off doesn't fit a ৳100 row
        ProductRequestDTO dear = product("Dear", "1200");
        dear.setDiscountAmount(new BigDecimal("150"));

        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> batch.createAll(List.of(dear, cheap), List.of(), List.of()));
        assertTrue(e.getMessage().startsWith("Row 2:"), e.getMessage());
        verify(creation, never()).createWithEverything(any(), any(), any());
    }

    @Test
    void aSizeListedTwiceStopsTheBatchBeforeAnythingIsSaved() {
        assertThrows(InvalidRequestException.class, () -> batch.createAll(
                List.of(product("One", "100")), List.of(size("M"), size("m")), List.of()));
        verify(creation, never()).createWithEverything(any(), any(), any());
    }

    @Test
    void aFailureWhileSavingIsReportedAgainstItsRowAndStopsTheRest() {
        when(creation.createWithEverything(any(), anyList(), anyList()))
                .thenReturn(new ProductResponseDTO())
                .thenThrow(new InvalidRequestException("Category not found: 9"));

        InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> batch.createAll(
                List.of(product("One", "100"), product("Two", "100"), product("Three", "100")),
                List.of(), List.of()));
        assertEquals("Row 2: Category not found: 9", e.getMessage());
        verify(creation, times(2)).createWithEverything(any(), any(), any());
    }

    @Test
    void anEmptyOrOversizedBatchIsRefused() {
        assertThrows(InvalidRequestException.class, () -> batch.createAll(List.of(), List.of(), List.of()));
        List<ProductRequestDTO> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) many.add(product("P" + i, "100"));
        assertThrows(InvalidRequestException.class, () -> batch.createAll(many, List.of(), List.of()));
        verify(creation, never()).createWithEverything(any(), any(), any());
    }
}
