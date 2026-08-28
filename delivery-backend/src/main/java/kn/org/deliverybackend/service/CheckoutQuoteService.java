package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.checkout.CheckoutQuoteRequestDTO;
import kn.org.deliverybackend.dto.checkout.CheckoutQuoteResponseDTO;

public interface CheckoutQuoteService {
    CheckoutQuoteResponseDTO quote(CheckoutQuoteRequestDTO request);
}
