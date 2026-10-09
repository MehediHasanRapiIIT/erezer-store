package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The sitemap lists what a shopper can open, and nothing that is hidden.
 */
class SitemapControllerTest {

    private static final String SHOP = "https://shop.erezer.com";

    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final kn.org.deliverybackend.repository.ContentPageRepository pages =
            mock(kn.org.deliverybackend.repository.ContentPageRepository.class);
    private final SitemapController controller = new SitemapController(products, categories, pages);
    private final List<Category> allCategories = new ArrayList<>();
    private final List<Product> allProducts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "storeUrl", SHOP + "/");
        when(categories.findAll()).thenReturn(allCategories);
        when(products.findAll()).thenReturn(allProducts);

        category(1, "hoodies", null, true, false);
        category(2, "zip-hoodies", 1L, true, false);
        category(3, "old-stock", null, false, false);      // switched off
        category(4, "old-stock-caps", 3L, true, false);    // under a switched-off category
        category(5, "removed", null, true, true);          // deleted

        product(10, 1L, false);
        product(11, 2L, false);
        product(12, null, false);    // no category
        product(13, 1L, true);       // deleted
        product(14, 3L, false);      // in a switched-off category
        product(15, 4L, false);      // in a subcategory of a switched-off category
        product(16, 5L, false);      // in a deleted category
    }

    @Test
    void listsStandingPagesLiveCategoriesAndLiveProducts() {
        List<String> urls = urls();

        assertTrue(urls.contains(SHOP + "/"));
        assertTrue(urls.contains(SHOP + "/shop"));
        assertTrue(urls.contains(SHOP + "/custom-design"));
        assertTrue(urls.contains(SHOP + "/hoodies"));
        assertTrue(urls.contains(SHOP + "/zip-hoodies"));
        assertTrue(urls.contains(SHOP + "/product/10"));
        assertTrue(urls.contains(SHOP + "/product/11"));
        assertTrue(urls.contains(SHOP + "/product/12"));
    }

    @Test
    void leavesOutWhatIsHiddenOrDeleted() {
        List<String> urls = urls();

        for (String hidden : List.of("/old-stock", "/old-stock-caps", "/removed",
                "/product/13", "/product/14", "/product/15", "/product/16")) {
            assertFalse(urls.contains(SHOP + hidden), hidden);
        }
        // 8 standing pages, 2 categories, 3 products.
        assertEquals(13, urls.size());
    }

    @Test
    void leavesOutPrivatePages() {
        for (String url : urls()) {
            for (String priv : List.of("/checkout", "/account", "/orders", "/wishlist", "/cart", "/admin")) {
                assertFalse(url.equals(SHOP + priv), url);
            }
        }
    }

    @Test
    void aNewProductAppearsByItself() {
        int before = urls().size();
        product(99, 2L, false);

        List<String> urls = urls();
        assertEquals(before + 1, urls.size());
        assertTrue(urls.contains(SHOP + "/product/99"));
    }

    @Test
    void isWellFormedXmlEvenWithAwkwardLetters() throws Exception {
        category(6, "tom&jerry", null, true, false);

        ResponseEntity<byte[]> response = controller.sitemap();
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(response.getBody()));

        assertEquals("urlset", doc.getDocumentElement().getNodeName());
        assertTrue(new String(response.getBody(), StandardCharsets.UTF_8).contains(SHOP + "/tom&amp;jerry"));
        assertTrue(String.valueOf(response.getHeaders().getContentType()).startsWith("application/xml"));
    }

    private List<String> urls() {
        String xml = new String(controller.sitemap().getBody(), StandardCharsets.UTF_8);
        List<String> urls = new ArrayList<>();
        int at = 0;
        while ((at = xml.indexOf("<loc>", at)) >= 0) {
            int end = xml.indexOf("</loc>", at);
            urls.add(xml.substring(at + 5, end));
            at = end;
        }
        return urls;
    }

    private void category(long id, String slug, Long parentId, boolean active, boolean deleted) {
        Category c = new Category();
        c.setId(id);
        c.setName(slug);
        c.setSlug(slug);
        c.setParentId(parentId);
        c.setIsActive(active);
        c.setDeleted(deleted);
        allCategories.add(c);
    }

    private void product(long id, Long categoryId, boolean deleted) {
        Product p = new Product();
        p.setId(id);
        p.setName("Product " + id);
        p.setCategoryId(categoryId);
        p.setPrice(new BigDecimal("850"));
        p.setDeleted(deleted);
        p.setUpdatedAt(new Date());
        allProducts.add(p);
    }
}
