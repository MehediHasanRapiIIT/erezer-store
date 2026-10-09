package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.variant.ProductFitsRequestDTO;
import kn.org.deliverybackend.dto.variant.ProductOptionDTO;
import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import kn.org.deliverybackend.dto.variant.VariantResponseDTO;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.enumeration.Fit;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A product's own options (colour and anything else), and what changing them
 * does to the variants it already has: counted stock is never lost by accident,
 * and a variant removed on purpose does not come back by itself.
 */
class ProductOptionsTest {

    private static final long PRODUCT = 7L;
    private static final String DS = Fit.DROP_SHOULDER.name(), RF = Fit.REGULAR_FIT.name();

    private final VariantRepository variants = mock(VariantRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final VariantServiceImpl variantService = new VariantServiceImpl(variants, products, inventory);
    private final kn.org.deliverybackend.repository.ProductImageRepository images =
            mock(kn.org.deliverybackend.repository.ProductImageRepository.class);
    private final ProductOptionsService service = new ProductOptionsService(products, variants, variantService, inventory, images);

    private final Product product = new Product();
    /** Everything ever saved for the product; the live ones are what the repository returns. */
    private final List<Variant> stored = new ArrayList<>();
    private long nextId = 100;

    @BeforeEach
    void setUp() {
        product.setId(PRODUCT);
        product.setSku("TS-00007");
        product.setDeleted(false);
        when(products.findById(PRODUCT)).thenReturn(Optional.of(product));
        when(products.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(variants.findByProductId(PRODUCT)).thenAnswer(inv ->
                new ArrayList<>(stored.stream().filter(v -> !Boolean.TRUE.equals(v.getDeleted())).toList()));
        when(variants.findById(anyLong())).thenAnswer(inv ->
                stored.stream().filter(v -> v.getId().equals(inv.<Long>getArgument(0))).findFirst());
        when(variants.findByProductIdAndSku(anyLong(), anyString())).thenAnswer(inv ->
                stored.stream().filter(v -> inv.getArgument(1).equals(v.getSku())).findFirst());
        when(variants.save(any(Variant.class))).thenAnswer(inv -> keep(inv.getArgument(0)));
        when(variants.saveAndFlush(any(Variant.class))).thenAnswer(inv -> keep(inv.getArgument(0)));
        when(variants.saveAll(any())).thenAnswer(inv -> {
            List<Variant> all = new ArrayList<>();
            for (Variant v : inv.<Iterable<Variant>>getArgument(0)) all.add(keep(v));
            return all;
        });
    }

    // ── a product with no options is exactly what it was ──────────────────────

    @Test
    void withoutOptionsNothingChanges() {
        variantService.createAll(PRODUCT, List.of(size(null, "S", 5), size(null, "M", 8)));

        assertEquals("S 5, M 8", now());
        VariantResponseDTO s = variantService.listForProduct(PRODUCT).get(0);
        assertNull(s.getOptionKey());
        assertTrue(s.getOptions().isEmpty());
        assertEquals("S", s.getName());
        assertEquals("TS-00007-S", s.getSku());
        assertTrue(service.get(PRODUCT).isEmpty());
    }

    // ── the first options ─────────────────────────────────────────────────────

    @Test
    void theFirstOptionMakesWhatTheProductHasItsFirstChoiceAndAddsTheRestEmpty() {
        variantService.createAll(PRODUCT, List.of(size(null, "S", 5), size(null, "M", 8)));

        service.set(PRODUCT, List.of(option("Colour", "COLOUR", "Black", "White", "Red")), false);

        assertEquals("Black S 5, Black M 8, White S 0, White M 0, Red S 0, Red M 0", now());
        VariantResponseDTO blackS = variantService.listForProduct(PRODUCT).get(0);
        assertEquals("TS-00007-S", blackS.getSku(), "the variant that was there keeps its SKU");
        assertEquals("Black / S", blackS.getName());
        assertEquals("Colour", blackS.getOptions().get(0).getOption());
        VariantResponseDTO whiteS = variantService.listForProduct(PRODUCT).get(2);
        assertEquals("TS-00007-WHITE-S", whiteS.getSku());
        assertEquals("White / S", whiteS.getName());
    }

    @Test
    void aProductWithNoSizesGetsOneVariantForEachChoice() {
        service.set(PRODUCT, List.of(option("Colour", "COLOUR", "Black", "Navy")), false);

        List<VariantResponseDTO> list = variantService.listForProduct(PRODUCT);
        assertEquals(2, list.size());
        assertEquals("Black", list.get(0).getName());
        assertNull(list.get(0).getSize());
        assertEquals("TS-00007-NAVY", list.get(1).getSku());
    }

    @Test
    void severalOptionsMakeEveryCombination() {
        variantService.createAll(PRODUCT, List.of(size(null, "M", 4)));

        ProductOptionsService.Result result = service.set(PRODUCT,
                List.of(option("Colour", "COLOUR", "Black", "White"), option("Sleeve", "TEXT", "Short", "Long")), false);

        assertEquals("Black / Short M 4, Black / Long M 0, White / Short M 0, White / Long M 0", now());
        assertEquals(4, result.variants().size());
        assertEquals("Black / Long / M", result.variants().get(1).getName());
        assertEquals(2, result.variants().get(1).getOptions().size());
    }

    @Test
    void optionsAndFitsMultiplyTogether() {
        variantService.createAll(PRODUCT, List.of(size(DS, "S", 5), size(RF, "S", 2)));

        service.set(PRODUCT, List.of(option("Colour", "COLOUR", "Black", "White")), false);
        assertEquals("Black DS S 5, Black RF S 2, White DS S 0, White RF S 0", now());

        // A size added without naming a combination is added to every one.
        variantService.createAll(PRODUCT, List.of(size(DS, "M", 0), size(RF, "M", 0)));
        assertEquals("Black DS S 5, Black DS M 0, Black RF S 2, Black RF M 0, White DS S 0, White DS M 0, White RF S 0, White RF M 0", now());
        assertEquals("Black / Drop Shoulder / M", variantService.listForProduct(PRODUCT).get(1).getName());
    }

    @Test
    void aFitAddedLaterIsAddedInEveryCombination() {
        variantService.createAll(PRODUCT, List.of(size(null, "S", 5)));
        service.set(PRODUCT, List.of(option("Colour", "COLOUR", "Black", "White")), false);

        variantService.setFits(PRODUCT, new ProductFitsRequestDTO(List.of(
                new ProductFitsRequestDTO.Choice(DS, null, false), new ProductFitsRequestDTO.Choice(RF, null, false))));

        assertEquals("Black DS S 5, Black RF S 0, White DS S 0, White RF S 0", now());
    }

    @Test
    void aProductWithColoursOnlyTakesItsFirstSizesInEveryColour() {
        service.set(PRODUCT, List.of(option("Colour", "COLOUR", "Black", "White")), false);
        assertEquals(2, variantService.listForProduct(PRODUCT).size());

        variantService.createAll(PRODUCT, List.of(size(null, "S", 5), size(null, "M", 8)));

        assertEquals("Black S 5, Black M 8, White S 5, White M 8", now());
        assertEquals("TS-00007-BLACK-S", variantService.listForProduct(PRODUCT).get(0).getSku());
        assertEquals("Black / S", variantService.listForProduct(PRODUCT).get(0).getName());
    }

    @Test
    void aPictureOfAChoiceThatIsRemovedSuitsEveryChoiceAgain() {
        List<ProductOptionDTO> options = service.set(PRODUCT, List.of(option("Colour", "COLOUR", "Black", "White")), false).options();
        var ofWhite = new kn.org.deliverybackend.entity.ProductImage();
        ofWhite.setOptionValueId(options.get(0).getValues().get(1).getId());
        var ofBlack = new kn.org.deliverybackend.entity.ProductImage();
        ofBlack.setOptionValueId(options.get(0).getValues().get(0).getId());
        when(images.findBelongingToAChoice(PRODUCT)).thenReturn(List.of(ofWhite, ofBlack));

        options.get(0).getValues().remove(1);   // White goes
        service.set(PRODUCT, options, false);

        assertNull(ofWhite.getOptionValueId());
        assertEquals(options.get(0).getValues().get(0).getId(), ofBlack.getOptionValueId());
    }

    // ── changing them later ───────────────────────────────────────────────────

    @Test
    void aChoiceAddedLaterComesWithNoStockAndTouchesNothingElse() {
        variantService.createAll(PRODUCT, List.of(size(null, "S", 5)));
        List<ProductOptionDTO> options = service.set(PRODUCT, List.of(option("Colour", "COLOUR", "Black", "White")), false).options();
        setStock("White", "S", 9);

        options.get(0).getValues().add(new ProductOptionDTO.Value(null, "Red", "#FF0000"));
        service.set(PRODUCT, options, false);

        assertEquals("Black S 5, White S 9, Red S 0", now());
    }

    @Test
    void aChoiceRemovedTakesItsVariantsAndRenamingOneKeepsThem() {
        variantService.createAll(PRODUCT, List.of(size(null, "S", 5)));
        List<ProductOptionDTO> options = service.set(PRODUCT, List.of(option("Colour", "COLOUR", "Black", "White", "Red")), false).options();
        setStock("White", "S", 9);
        String whiteSku = variantService.listForProduct(PRODUCT).get(1).getSku();

        options.get(0).getValues().remove(2);                 // Red goes
        options.get(0).getValues().get(1).setValue("Off White");   // White is renamed
        options.get(0).setName("Color");
        service.set(PRODUCT, options, false);

        assertEquals("Black S 5, Off White S 9", now());
        VariantResponseDTO white = variantService.listForProduct(PRODUCT).get(1);
        assertEquals("Off White / S", white.getName());
        assertEquals(whiteSku, white.getSku(), "a rename leaves the SKU alone");
        assertEquals("Color", white.getOptions().get(0).getOption());
    }

    @Test
    void aVariantRemovedOnPurposeStaysRemovedUntilAskedBack() {
        variantService.createAll(PRODUCT, List.of(size(null, "S", 5), size(null, "M", 5)));
        List<ProductOptionDTO> options = service.set(PRODUCT, List.of(option("Colour", "COLOUR", "Black", "White")), false).options();
        Long whiteM = variantService.listForProduct(PRODUCT).get(3).getId();
        variantService.delete(PRODUCT, whiteM);
        assertEquals("Black S 5, Black M 5, White S 0", now());

        // Saving the options again, even with a new choice, does not bring it back.
        options.get(0).getValues().add(new ProductOptionDTO.Value(null, "Red", null));
        options = service.set(PRODUCT, options, false).options();
        assertEquals("Black S 5, Black M 5, White S 0, Red S 0, Red M 0", now());

        service.set(PRODUCT, options, true);
        assertEquals("Black S 5, Black M 5, White S 0, White M 0, Red S 0, Red M 0", now());
    }

    @Test
    void anOptionRemovedMergesWhatIsLeftAndAddsTheStockTogether() {
        variantService.createAll(PRODUCT, List.of(size(null, "S", 5)));
        List<ProductOptionDTO> options = service.set(PRODUCT,
                List.of(option("Colour", "COLOUR", "Black", "White"), option("Sleeve", "TEXT", "Short", "Long")), false).options();
        setStock("Black / Long", "S", 3);
        setStock("White / Short", "S", 4);
        setStock("White / Long", "S", 1);
        String keptSku = variantService.listForProduct(PRODUCT).get(0).getSku();

        service.set(PRODUCT, List.of(options.get(0)), false);   // Sleeve goes

        assertEquals("Black S 8, White S 5", now());
        assertEquals(keptSku, variantService.listForProduct(PRODUCT).get(0).getSku(), "the first choice's variant is the one kept");
    }

    @Test
    void removingEveryOptionLeavesAPlainProductWithAllItsStock() {
        variantService.createAll(PRODUCT, List.of(size(null, "S", 5), size(null, "M", 2)));
        service.set(PRODUCT, List.of(option("Colour", "COLOUR", "Black", "White")), false);
        setStock("White", "S", 4);

        service.set(PRODUCT, List.of(), false);

        assertEquals("S 9, M 2", now());
        assertNull(product.getOptionsJson());
        assertNull(variantService.listForProduct(PRODUCT).get(0).getOptionKey());
        assertEquals("S", variantService.listForProduct(PRODUCT).get(0).getName());
    }

    @Test
    void anOptionAddedToAProductThatHasOneAlreadyKeepsEveryVariantUnderItsFirstChoice() {
        variantService.createAll(PRODUCT, List.of(size(null, "S", 5)));
        List<ProductOptionDTO> options = new ArrayList<>(service.set(PRODUCT, List.of(option("Colour", "COLOUR", "Black", "White")), false).options());
        setStock("White", "S", 7);

        options.add(option("Sleeve", "TEXT", "Short", "Long"));
        service.set(PRODUCT, options, false);

        assertEquals("Black / Short S 5, Black / Long S 0, White / Short S 7, White / Long S 0", now());
    }

    @Test
    void theOrderOfTheOptionsCanChangeWithoutChangingAnyVariant() {
        variantService.createAll(PRODUCT, List.of(size(null, "S", 5)));
        List<ProductOptionDTO> options = service.set(PRODUCT,
                List.of(option("Colour", "COLOUR", "Black", "White"), option("Sleeve", "TEXT", "Short", "Long")), false).options();
        List<Long> idsBefore = variantService.listForProduct(PRODUCT).stream().map(VariantResponseDTO::getId).sorted().toList();

        service.set(PRODUCT, List.of(options.get(1), options.get(0)), false);

        assertEquals(idsBefore, variantService.listForProduct(PRODUCT).stream().map(VariantResponseDTO::getId).sorted().toList());
        assertEquals("Short / Black S 5, Short / White S 0, Long / Black S 0, Long / White S 0", now());
    }

    // ── what is refused ───────────────────────────────────────────────────────

    @Test
    void optionsNeedNamesAndChoicesAndMayNotRepeat() {
        assertMessage("Give every option a name", () -> service.set(PRODUCT, List.of(option(" ", "TEXT", "A")), false));
        assertMessage("at least one choice", () -> service.set(PRODUCT, List.of(option("Colour", "COLOUR")), false));
        assertMessage("Two options are called colour", () -> service.set(PRODUCT,
                List.of(option("Colour", "COLOUR", "Black"), option("colour", "TEXT", "Red")), false));
        assertMessage("has black twice", () -> service.set(PRODUCT, List.of(option("Colour", "COLOUR", "Black", "black")), false));
        assertMessage("have their own sections", () -> service.set(PRODUCT, List.of(option("Size", "TEXT", "S", "M")), false));
        assertNull(product.getOptionsJson());
    }

    @Test
    void aTypoCannotMakeThousandsOfVariants() {
        variantService.createAll(PRODUCT, List.of(size(null, "S", 1), size(null, "M", 1), size(null, "L", 1)));
        List<ProductOptionDTO> many = new ArrayList<>();
        for (String name : List.of("A", "B", "C")) many.add(option(name, "TEXT", "1", "2", "3", "4", "5", "6"));

        InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> service.set(PRODUCT, many, false));
        assertTrue(e.getMessage().contains("648 variants"), e.getMessage());
        assertEquals("S 1, M 1, L 1", now(), "and nothing was changed");
    }

    @Test
    void aVariantNeedsOneOfEachOptionAndCannotBeThereTwice() {
        List<ProductOptionDTO> options = service.set(PRODUCT, List.of(option("Colour", "COLOUR", "Black", "White")), false).options();
        String colour = options.get(0).getId(), black = options.get(0).getValues().get(0).getId();

        VariantRequestDTO noChoice = size(null, "S", 1);
        assertMessage("one of each of this product's options: Colour", () -> variantService.create(PRODUCT, noChoice));
        VariantRequestDTO unknown = size(null, "S", 1);
        unknown.setOptions(Map.of(colour, "nope"));
        assertMessage("one of each", () -> variantService.create(PRODUCT, unknown));

        VariantRequestDTO blackS = size(null, "S", 1);
        blackS.setOptions(Map.of(colour, black));
        variantService.create(PRODUCT, blackS);
        VariantRequestDTO again = size(null, "S", 1);
        again.setOptions(Map.of(colour, black));
        assertMessage("already has Black, size S", () -> variantService.create(PRODUCT, again));
    }

    @Test
    void aProductWithoutOptionsRefusesAChoice() {
        VariantRequestDTO r = size(null, "S", 1);
        r.setOptions(Map.of("x", "y"));
        assertMessage("has no options", () -> variantService.create(PRODUCT, r));
    }

    @Test
    void eachVariantHasItsOwnPriceAndSkusNeverClash() {
        variantService.createAll(PRODUCT, List.of(size(null, "S", 5)));
        service.set(PRODUCT, List.of(option("Colour", "COLOUR", "Navy Blue", "Navy Blue Dark")), false);

        List<VariantResponseDTO> list = variantService.listForProduct(PRODUCT);
        assertNotEquals(list.get(0).getSku(), list.get(1).getSku());

        VariantRequestDTO change = size(null, "S", 2);
        change.setPriceOverride(new BigDecimal("950"));
        variantService.update(PRODUCT, list.get(1).getId(), change);
        List<VariantResponseDTO> after = variantService.listForProduct(PRODUCT);
        assertEquals(0, new BigDecimal("950").compareTo(after.get(1).getPriceOverride()));
        assertNull(after.get(0).getPriceOverride());
        assertEquals(list.get(1).getOptionKey(), after.get(1).getOptionKey(), "changing a variant keeps its combination");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Variant keep(Variant v) {
        if (v.getId() == null) v.setId(nextId++);
        if (v.getDeleted() == null) v.setDeleted(false);
        if (!stored.contains(v)) stored.add(v);
        return v;
    }

    private static VariantRequestDTO size(String fit, String size, int stock) {
        VariantRequestDTO r = new VariantRequestDTO();
        r.setFit(fit);
        r.setSize(size);
        r.setStockQuantity(stock);
        return r;
    }

    private static ProductOptionDTO option(String name, String kind, String... values) {
        ProductOptionDTO o = new ProductOptionDTO();
        o.setName(name);
        o.setKind(kind);
        for (String value : values) o.getValues().add(new ProductOptionDTO.Value(null, value, null));
        return o;
    }

    private void setStock(String combination, String size, int stock) {
        Variant v = stored.stream().filter(x -> !Boolean.TRUE.equals(x.getDeleted()))
                .filter(x -> (combination + " / " + size).equals(x.getName())).findFirst().orElseThrow();
        v.setStockQuantity(stock);
    }

    /** "Black DS S 5, White S 0": what the product has now, in the order it is listed. */
    private String now() {
        return variantService.listForProduct(PRODUCT).stream()
                .map(v -> String.join(" ", java.util.stream.Stream.of(
                        v.getOptionLabel(),
                        v.getFit() == null ? null : (v.getFit().equals(DS) ? "DS" : "RF"),
                        v.getSize(),
                        String.valueOf(v.getStockQuantity())).filter(java.util.Objects::nonNull).toList()))
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static void assertMessage(String part, org.junit.jupiter.api.function.Executable action) {
        InvalidRequestException e = assertThrows(InvalidRequestException.class, action);
        assertTrue(e.getMessage().contains(part), e.getMessage());
    }
}
