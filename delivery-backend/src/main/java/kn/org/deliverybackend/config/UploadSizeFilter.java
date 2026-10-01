package kn.org.deliverybackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * How large an upload may be, decided by where it is going.
 *
 * <p>Product photos are stored exactly as staff upload them — never shrunk or
 * converted — so adding a product, or a whole shoot of them, can be a large
 * request. Those two staff-only saves may send up to
 * {@code app.uploads.max-product-upload-bytes}. Every other upload, including
 * everything customers can reach, stays at {@code app.uploads.max-request-bytes}.
 *
 * <p>Spring's own multipart limit is one number for the whole server, so it is
 * set to the larger ceiling and this filter holds everything else to the
 * smaller one. It judges by the declared length, before the body is read, so an
 * oversized upload is turned away without the server taking it in. Browsers
 * always declare the length of a form upload; one that doesn't is refused.
 */
@Component
// After Spring Security (-100): a refusal then carries the CORS headers, so the
// admin and shop can read its message, and a request without a login is turned
// away by security first. Nothing before this point reads the body.
@Order(0)
public class UploadSizeFilter extends OncePerRequestFilter {

    /** The two saves that may carry a product's photos at their original size. */
    static final Set<String> PRODUCT_UPLOADS = Set.of("/admin/products/full", "/admin/products/batch");

    private final long normalLimit;
    private final long productLimit;

    public UploadSizeFilter(@Value("${app.uploads.max-request-bytes:62914560}") long normalLimit,
                            @Value("${app.uploads.max-product-upload-bytes:251658240}") long productLimit) {
        this.normalLimit = normalLimit;
        this.productLimit = productLimit;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String type = request.getContentType();
        return type == null || !type.toLowerCase().startsWith("multipart/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        boolean productUpload = isProductUpload(request);
        long limit = productUpload ? productLimit : normalLimit;
        long length = request.getContentLengthLong();

        if (length < 0 && !productUpload) {
            // The product saves still have Spring's ceiling behind them; nothing else may stream unmeasured.
            refuse(response, HttpServletResponse.SC_LENGTH_REQUIRED, "Send the upload with its size.");
            return;
        }
        if (length > limit) {
            refuse(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, productUpload
                    ? "These pictures are too large to send at once. Save part of them first."
                    : "The file is too large.");
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean isProductUpload(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return false;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return PRODUCT_UPLOADS.contains(path);
    }

    private static void refuse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
