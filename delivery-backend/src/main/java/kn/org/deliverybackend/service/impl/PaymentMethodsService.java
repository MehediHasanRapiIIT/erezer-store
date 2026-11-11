package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.payment.PaymentMethodsChangeDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Saves the payment switches an admin sets on products or a category. Answers the message the admin sees. */
@Service
@RequiredArgsConstructor
public class PaymentMethodsService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public String setOnProducts(PaymentMethodsChangeDTO change) {
        requireSomething(change);
        if (change.getProductIds() == null || change.getProductIds().isEmpty()) {
            throw new InvalidRequestException("Choose at least one product.");
        }
        List<Product> products = productRepository.findAllById(change.getProductIds()).stream()
                .filter(p -> !Boolean.TRUE.equals(p.getDeleted())).toList();
        if (products.isEmpty()) throw new InvalidRequestException("There are no products to change.");
        for (Product p : products) {
            PaymentMethodRules.apply(p, change.getCod(), change.getBkash(), change.getCard());
            if (noWayLeft(p.getPayCod(), p.getPayBkash(), p.getPayCard())) {
                throw new InvalidRequestException(p.getName() + " needs at least one way to pay.");
            }
        }
        productRepository.saveAll(products);
        String what = products.size() == 1 ? products.get(0).getName() : products.size() + " products";
        return "Ways to pay for " + what + ": " + summary(change) + ".";
    }

    @Transactional
    public String setOnCategory(PaymentMethodsChangeDTO change) {
        requireSomething(change);
        if (change.getCategoryId() == null) throw new InvalidRequestException("Choose a category.");
        Category category = categoryRepository.findById(change.getCategoryId())
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + change.getCategoryId()));
        PaymentMethodRules.apply(category, change.getCod(), change.getBkash(), change.getCard());
        if (noWayLeft(category.getPayCod(), category.getPayBkash(), category.getPayCard())) {
            throw new InvalidRequestException(category.getName() + " needs at least one way to pay.");
        }
        categoryRepository.save(category);
        return "Ways to pay for " + category.getName() + ": " + summary(change)
                + ". Its products follow it, unless a product sets its own.";
    }

    private static void requireSomething(PaymentMethodsChangeDTO change) {
        if (blank(change.getCod()) && blank(change.getBkash()) && blank(change.getCard())) {
            throw new InvalidRequestException("Choose what to change.");
        }
    }

    private static boolean noWayLeft(Boolean cod, Boolean bkash, Boolean card) {
        return Boolean.FALSE.equals(cod) && Boolean.FALSE.equals(bkash) && Boolean.FALSE.equals(card);
    }

    /** "Cash on delivery not allowed, bKash allowed" - only what was changed. */
    private static String summary(PaymentMethodsChangeDTO change) {
        List<String> parts = new ArrayList<>();
        add(parts, "CASH", change.getCod());
        add(parts, "BKASH", change.getBkash());
        add(parts, "CARD", change.getCard());
        return String.join(", ", parts);
    }

    private static void add(List<String> parts, String method, String value) {
        if (blank(value)) return;
        String what = switch (value.trim().toUpperCase()) {
            case "ALLOW" -> "allowed";
            case "BLOCK" -> "not allowed";
            default -> "same as category";
        };
        parts.add(PaymentMethodRules.label(method) + " " + what);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
