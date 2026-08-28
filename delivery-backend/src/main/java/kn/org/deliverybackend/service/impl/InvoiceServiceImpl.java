package kn.org.deliverybackend.service.impl;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import kn.org.deliverybackend.dto.OrderDTO;
import kn.org.deliverybackend.dto.OrderItemDTO;
import kn.org.deliverybackend.dto.invoice.InvoiceSendResultDTO;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.service.EmailAttachment;
import kn.org.deliverybackend.service.EmailService;
import kn.org.deliverybackend.service.InvoiceService;
import kn.org.deliverybackend.service.OrderHistoryService;
import kn.org.deliverybackend.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final OrderRepository orderRepository;
    private final OrderHistoryService orderHistoryService;
    private final SpringTemplateEngine templateEngine;
    private final EmailService emailService;
    private final SmsService smsService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    // ── PDF ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        OrderDTO dto = orderHistoryService.getOrderByIdForAdmin(orderId);

        String html = templateEngine.process("invoice", buildContext(order, dto));
        // openhtmltopdf needs strict XHTML — normalise Thymeleaf's HTML output.
        Document doc = Jsoup.parse(html);
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(doc.html(), null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate invoice PDF: " + e.getMessage(), e);
        }
    }

    // ── Send (email + SMS) ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public InvoiceSendResultDTO sendInvoice(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        byte[] pdf = generateInvoicePdf(orderId);
        String ref = invoiceNumber(order.getId());

        boolean emailSent = false;
        String email = order.getCustomerEmail();
        if (email != null && !email.isBlank()) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("invoiceNumber", ref);
            vars.put("customerName", order.getCustomerName());
            vars.put("total", money(order.getTotalAmount()));
            emailService.sendWithAttachments(
                    email,
                    "Your Erezer invoice " + ref,
                    "invoice-email",
                    vars,
                    List.of(new EmailAttachment(ref + ".pdf", "application/pdf", pdf)));
            emailSent = true;
        }

        boolean smsSent = false;
        String phone = order.getCustomerPhone();
        if (phone != null && !phone.isBlank()) {
            String sms = "Erezer: Invoice " + ref + " for your order (" + money(order.getTotalAmount())
                    + "). We've emailed your invoice. Thank you!";
            smsService.send(phone, sms);
            smsSent = true;
        }

        String message = "Invoice " + ref + ": "
                + (emailSent ? "emailed" : "no email on file")
                + (smsSent ? ", SMS sent" : ", no phone on file") + ".";
        return InvoiceSendResultDTO.builder()
                .emailSent(emailSent).smsSent(smsSent).message(message).build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private Context buildContext(Order order, OrderDTO dto) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (dto.getOrderItems() != null) {
            for (OrderItemDTO it : dto.getOrderItems()) {
                Map<String, Object> row = new HashMap<>();
                row.put("name", it.getProductName() != null ? it.getProductName() : "Item");
                row.put("size", it.getVariantSize() != null && !it.getVariantSize().isBlank()
                        ? it.getVariantSize() : "—");
                int qty = it.getQuantity() == null ? 0 : it.getQuantity();
                BigDecimal unit = it.getPriceAtOrder() == null ? BigDecimal.ZERO : it.getPriceAtOrder();
                row.put("quantity", qty);
                row.put("unit", money(unit));
                row.put("total", money(unit.multiply(BigDecimal.valueOf(qty))));
                items.add(row);
            }
        }

        BigDecimal subtotal = order.getSubtotalAmount();
        BigDecimal discount = order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount();
        BigDecimal shipping = order.getShippingFee() != null ? order.getShippingFee()
                : (order.getDeliveryCharge() != null ? BigDecimal.valueOf(order.getDeliveryCharge()) : BigDecimal.ZERO);

        int itemCount = items.stream().mapToInt(r -> (int) r.getOrDefault("quantity", 0)).sum();

        Context ctx = new Context();
        Map<String, Object> m = new HashMap<>();
        m.put("invoiceNumber", invoiceNumber(order.getId()));
        m.put("invoiceDate", LocalDateTime.now().format(DATE_FMT));
        m.put("orderDate", order.getCreatedAt() == null ? "—" :
                LocalDateTime.ofInstant(order.getCreatedAt().toInstant(), ZoneId.systemDefault()).format(DATE_FMT));
        m.put("itemCount", itemCount);
        m.put("status", order.getOrderStatus());
        m.put("customerName", dto.getCustomerName() != null ? dto.getCustomerName() : order.getCustomerName());
        m.put("customerPhone", order.getCustomerPhone());
        m.put("customerEmail", order.getCustomerEmail());
        m.put("deliveryAddress", order.getDeliveryAddress());
        m.put("paymentMethod", order.getPaymentMethod());
        m.put("items", items);
        m.put("subtotal", money(subtotal != null ? subtotal : BigDecimal.ZERO));
        m.put("hasDiscount", discount.signum() > 0);
        m.put("discount", "− " + money(discount));
        m.put("shipping", money(shipping));
        m.put("total", money(order.getTotalAmount()));
        ctx.setVariables(m);
        return ctx;
    }

    private String invoiceNumber(UUID id) {
        return "INV-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /** Formats an amount with thousands grouping. Uses "Tk" to stay ASCII-safe for the PDF font. */
    private String money(BigDecimal v) {
        BigDecimal value = v == null ? BigDecimal.ZERO : v;
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return "Tk " + nf.format(value);
    }
}
