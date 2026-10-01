package kn.org.deliverybackend.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Product photos go up at their original size, so the two "add product" saves
 * may be large; every other upload stays small.
 */
class UploadSizeFilterTest {

    private static final long MB = 1024 * 1024;
    private final UploadSizeFilter filter = new UploadSizeFilter(60 * MB, 240 * MB);

    /** An upload declaring this size; -1 for one that won't say. No body is needed: the filter judges the declared size. */
    private static MockHttpServletRequest upload(String method, String path, long length) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path) {
            @Override
            public long getContentLengthLong() {
                return length;
            }
        };
        request.setContentType("multipart/form-data; boundary=x");
        return request;
    }

    /** Runs the filter; true when the request was let through. */
    private boolean passes(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        try {
            verify(chain).doFilter(any(), any());
            return true;
        } catch (AssertionError notCalled) {
            verify(chain, never()).doFilter(any(), any());
            return false;
        }
    }

    @Test
    void aWholeShootMayGoToTheBatchSave() throws Exception {
        assertTrue(passes(upload("POST", "/admin/products/batch", 200 * MB), new MockHttpServletResponse()));
    }

    @Test
    void andToTheOneProductSave() throws Exception {
        assertTrue(passes(upload("POST", "/admin/products/full", 150 * MB), new MockHttpServletResponse()));
    }

    @Test
    void evenThoseHaveACeiling() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals(false, passes(upload("POST", "/admin/products/batch", 241 * MB), response));
        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("Save part of them first"));
    }

    @Test
    void everyOtherUploadStaysAt60MB() throws Exception {
        assertTrue(passes(upload("POST", "/api/custom-design/upload", 59 * MB), new MockHttpServletResponse()));

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals(false, passes(upload("POST", "/api/custom-design/upload", 61 * MB), response));
        assertEquals(413, response.getStatus());
    }

    @Test
    void theLargeAllowanceIsForThoseExactSavesOnly() throws Exception {
        // A look-alike path, or another method on the same path, gets the normal limit.
        for (MockHttpServletRequest r : new MockHttpServletRequest[]{
                upload("POST", "/admin/products/batch/extra", 100 * MB),
                upload("PUT", "/admin/products/batch", 100 * MB),
                upload("POST", "/admin/products/1/images", 100 * MB)}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertEquals(false, passes(r, response), r.getMethod() + " " + r.getRequestURI());
            assertEquals(413, response.getStatus());
        }
    }

    @Test
    void anUploadThatWontSayItsSizeIsRefusedOutsideTheProductSaves() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals(false, passes(upload("POST", "/api/custom-design/upload", -1), response));
        assertEquals(411, response.getStatus());

        // The product saves still have Spring's own ceiling behind them.
        assertTrue(passes(upload("POST", "/admin/products/batch", -1), new MockHttpServletResponse()));
    }

    @Test
    void requestsThatArentUploadsAreLeftAlone() throws Exception {
        MockHttpServletRequest json = new MockHttpServletRequest("POST", "/api/checkout/quote") {
            @Override
            public long getContentLengthLong() {
                return 500 * MB;
            }
        };
        json.setContentType("application/json");
        assertTrue(passes(json, new MockHttpServletResponse()));
    }
}
