package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.product.ProductRequestDTO;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.service.FileStorageService;
import kn.org.deliverybackend.service.ProductImageService;
import kn.org.deliverybackend.service.ProductService;
import kn.org.deliverybackend.service.VariantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Add product" saves the product, its pictures and its sizes together, or
 * nothing — and never leaves a picture in storage for a product that was not
 * saved.
 */
class ProductCreationTest {

    private final ProductService productService = mock(ProductService.class);
    private final ProductImageService imageService = mock(ProductImageService.class);
    private final VariantService variantService = mock(VariantService.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final ProductCreationServiceImpl service =
            new ProductCreationServiceImpl(productService, imageService, variantService, storage, mock(ProductOptionsService.class));

    /** What storage handed back, in order. */
    private final List<String> storedUrls = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // The transaction a real request would run in, so the clean-up hook can be registered.
        TransactionSynchronizationManager.initSynchronization();

        ProductResponseDTO created = new ProductResponseDTO();
        created.setId(42L);
        created.setName("Linen Shirt");
        when(productService.createProduct(any(), isNull())).thenReturn(created);
        when(productService.getProductById(42L)).thenReturn(created);
        when(storage.uploadFile(any(MultipartFile.class))).thenAnswer(inv -> {
            String url = "http://files/erezer/pic-" + (storedUrls.size() + 1) + ".png";
            storedUrls.add(url);
            return url;
        });
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0};

    private static MultipartFile picture(String name) {
        return new MockMultipartFile("pictures", name, "image/png", PNG);
    }

    private static MultipartFile notAPicture(String name) {
        return new MockMultipartFile("pictures", name, "image/png", "just some text, honestly".getBytes());
    }

    private static ProductRequestDTO product() {
        ProductRequestDTO p = new ProductRequestDTO();
        p.setName("Linen Shirt");
        p.setPrice(new BigDecimal("1000"));
        return p;
    }

    private static VariantRequestDTO size(String s, int stock) {
        VariantRequestDTO r = new VariantRequestDTO();
        r.setSize(s);
        r.setStockQuantity(stock);
        return r;
    }

    /** What happens when the transaction ends, as Spring would report it. */
    private static void transactionEnds(int status) {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(status);
        }
    }

    // ── when it all goes well ──────────────────────────────────────────────────

    @Test
    void savesTheProductItsPicturesInOrderAndItsSizes() {
        service.createWithEverything(product(),
                List.of(size("S", 5), size("M", 8)),
                List.of(picture("front.png"), picture("back.png"), picture("side.png")));

        InOrder order = inOrder(productService, imageService, variantService);
        order.verify(productService).createProduct(any(), isNull());
        order.verify(imageService).attach(42L, storedUrls.get(0), null, 0, true);
        order.verify(imageService).attach(42L, storedUrls.get(1), null, 1, false);
        order.verify(imageService).attach(42L, storedUrls.get(2), null, 2, false);
        order.verify(variantService).createAll(eq(42L), anyList());
    }

    @Test
    void picturesAndSizesAreOptional() {
        service.createWithEverything(product(), null, null);
        verify(productService).createProduct(any(), isNull());
        verify(storage, never()).uploadFile(any(MultipartFile.class));
        verify(variantService, never()).createAll(anyLong(), anyList());
    }

    @Test
    void aSuccessfulSaveKeepsEveryPicture() {
        service.createWithEverything(product(), List.of(), List.of(picture("a.png"), picture("b.png")));
        transactionEnds(TransactionSynchronization.STATUS_COMMITTED);
        verify(storage, never()).deleteByUrl(anyString());
    }

    // ── when something is wrong before anything is stored ─────────────────────

    @Test
    void somethingThatIsNotAPictureStopsItBeforeAnythingIsStored() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> service.createWithEverything(product(), List.of(),
                        List.of(picture("a.png"), picture("b.png"), notAPicture("notes.png"))));
        assertTrue(e.getMessage().startsWith("Picture 3"), e.getMessage());
        verify(productService, never()).createProduct(any(), any());
        verify(storage, never()).uploadFile(any(MultipartFile.class));
    }

    @Test
    void aSizeListedTwiceStopsItBeforeAnythingIsStored() {
        assertThrows(InvalidRequestException.class,
                () -> service.createWithEverything(product(),
                        List.of(size("M", 1), size("M", 2)), List.of(picture("a.png"))));
        verify(productService, never()).createProduct(any(), any());
        verify(storage, never()).uploadFile(any(MultipartFile.class));
    }

    @Test
    void aDiscountLargerThanThePriceStopsItBeforeAnythingIsStored() {
        ProductRequestDTO p = product();
        p.setDiscountAmount(new BigDecimal("1500"));
        assertThrows(InvalidRequestException.class,
                () -> service.createWithEverything(p, List.of(), List.of(picture("a.png"))));
        verify(storage, never()).uploadFile(any(MultipartFile.class));
    }

    @Test
    void tooManyPicturesAreRefused() {
        List<MultipartFile> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) eleven.add(picture("p" + i + ".png"));
        assertThrows(InvalidRequestException.class,
                () -> service.createWithEverything(product(), List.of(), eleven));
        verify(storage, never()).uploadFile(any(MultipartFile.class));
    }

    // ── when it fails part way ─────────────────────────────────────────────────

    @Test
    void aFailureAfterThePicturesAreStoredRemovesThemAgain() {
        when(variantService.createAll(anyLong(), anyList()))
                .thenThrow(new InvalidRequestException("This product already has size M."));

        assertThrows(InvalidRequestException.class,
                () -> service.createWithEverything(product(), List.of(size("M", 1)),
                        List.of(picture("a.png"), picture("b.png"))));
        assertEquals(2, storedUrls.size(), "both pictures had reached storage");

        transactionEnds(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage).deleteByUrl(storedUrls.get(0));
        verify(storage).deleteByUrl(storedUrls.get(1));
    }

    @Test
    void aPictureThatFailsToRecordIsStillRemoved() {
        // Stored, then the database refuses the row: the address was already noted.
        when(imageService.attach(anyLong(), anyString(), any(), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("database went away"));

        assertThrows(RuntimeException.class,
                () -> service.createWithEverything(product(), List.of(), List.of(picture("a.png"))));
        transactionEnds(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage).deleteByUrl(storedUrls.get(0));
    }

    @Test
    void aStorageHiccupWhileTidyingUpDoesNotHideTheRest() {
        when(variantService.createAll(anyLong(), anyList())).thenThrow(new InvalidRequestException("nope"));
        assertThrows(InvalidRequestException.class,
                () -> service.createWithEverything(product(), List.of(size("S", 1)),
                        List.of(picture("a.png"), picture("b.png"))));
        org.mockito.Mockito.doThrow(new RuntimeException("storage is down"))
                .when(storage).deleteByUrl(storedUrls.get(0));

        transactionEnds(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage).deleteByUrl(storedUrls.get(1));
    }
}
