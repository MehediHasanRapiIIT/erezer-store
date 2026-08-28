package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.invoice.InvoiceSendResultDTO;

import java.util.UUID;

public interface InvoiceService {

    /** Renders the order's invoice as PDF bytes. */
    byte[] generateInvoicePdf(UUID orderId);

    /** Generates the invoice PDF, emails it to the customer, and sends a short SMS. */
    InvoiceSendResultDTO sendInvoice(UUID orderId);
}
