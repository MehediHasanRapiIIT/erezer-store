package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.product.CodeChangeRequestDTO;
import kn.org.deliverybackend.dto.response.product.CodeChangePreviewDTO;
import kn.org.deliverybackend.dto.response.product.CodeChangePreviewDTO.Row;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.service.CategoryCodeChange;
import kn.org.deliverybackend.service.CategoryCodeChange.CodeMode;
import kn.org.deliverybackend.service.CodeChangeService;
import kn.org.deliverybackend.util.SearchText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CodeChangeServiceImpl implements CodeChangeService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    /** One product's new code, worked out but not yet saved. */
    private record Planned(Product product, String code, Row row) {
    }

    @Override
    @Transactional(readOnly = true)
    public CodeChangePreviewDTO preview(CodeChangeRequestDTO request, String q, int page, int size) {
        Category category = checked(request);
        List<Planned> plan = plan(category, request);
        String text = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Row> matching = plan.stream()
                .map(Planned::row)
                .filter(r -> text.isEmpty() || contains(r.name(), text) || contains(r.oldCode(), text)
                        || contains(r.newCode(), text))
                .toList();
        int pageSize = SearchText.pageSize(size);
        int totalPages = Math.max(1, (matching.size() + pageSize - 1) / pageSize);
        int current = Math.min(Math.max(page, 0), totalPages - 1);
        List<Row> rows = matching.subList(Math.min(current * pageSize, matching.size()),
                Math.min((current + 1) * pageSize, matching.size()));
        return result(category, plan, current, pageSize, matching.size(), totalPages, rows);
    }

    @Override
    @Transactional
    public CodeChangePreviewDTO apply(CodeChangeRequestDTO request) {
        Category category = checked(request);
        List<Planned> plan = plan(category, request);
        List<Planned> changing = plan.stream().filter(p -> p.row().changed()).toList();
        if (changing.isEmpty()) {
            throw new InvalidRequestException("Nothing to change: every product is unticked or already has this code.");
        }
        List<String> problems = plan.stream()
                .filter(p -> p.row().problem() != null)
                .map(p -> p.row().name() + ": " + p.row().problem())
                .toList();
        if (!problems.isEmpty()) {
            throw new InvalidRequestException("Nothing was changed. " + String.join(" ", problems));
        }
        List<Product> saved = new ArrayList<>();
        for (Planned p : changing) {
            p.product().setProductCode(p.code());
            saved.add(p.product());
        }
        productRepository.saveAll(saved);
        List<Row> all = plan.stream().map(Planned::row).toList();
        return result(category, plan, 0, all.size(), all.size(), 1, all);
    }

    private Category checked(CodeChangeRequestDTO request) {
        String invalid = CategoryCodeChange.invalidReason(request.mode(), request.code());
        if (invalid != null) {
            throw new InvalidRequestException(invalid);
        }
        return categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.categoryId()));
    }

    /**
     * The category's products in name order. Every ticked product gets the same
     * code, or a number of its own when the numbered way is chosen; either way an
     * unticked product keeps its code. With numbers, a number an unticked product
     * already holds with that prefix is skipped, so applying twice never gives two
     * products the same numbered code.
     */
    private List<Planned> plan(Category category, CodeChangeRequestDTO request) {
        String value = request.code().trim();
        List<Product> products = productRepository.findLiveByCategory(category.getId());
        Set<Long> excluded = new HashSet<>(request.excludedProductIds() == null
                ? List.of() : request.excludedProductIds());
        Set<Integer> taken = new HashSet<>();
        if (request.mode() == CodeMode.NUMBERED) {
            for (Product p : products) {
                if (excluded.contains(p.getId())) {
                    int number = CategoryCodeChange.numberOf(value, p.getProductCode());
                    if (number > 0) taken.add(number);
                }
            }
        }

        List<Planned> plan = new ArrayList<>();
        int next = 1;
        for (Product p : products) {
            if (excluded.contains(p.getId())) {
                plan.add(new Planned(p, p.getProductCode(), row(p, p.getProductCode(), false, null)));
                continue;
            }
            String code;
            if (request.mode() == CodeMode.NUMBERED) {
                while (taken.contains(next)) {
                    next++;
                }
                code = CategoryCodeChange.code(value, next);
                next++;
            } else {
                code = value;
            }
            String problem = code.length() > CategoryCodeChange.MAX_CODE_LENGTH
                    ? "The code would be longer than " + CategoryCodeChange.MAX_CODE_LENGTH + " characters." : null;
            plan.add(new Planned(p, code, row(p, code, !code.equals(p.getProductCode()), problem)));
        }
        return plan;
    }

    private static Row row(Product product, String newCode, boolean changed, String problem) {
        return new Row(product.getId(), product.getName(), product.getImageUrl(),
                product.getProductCode(), newCode, changed, problem);
    }

    private static CodeChangePreviewDTO result(Category category, List<Planned> plan, int page, int size,
                                               int totalRows, int totalPages, List<Row> rows) {
        List<Row> all = plan.stream().map(Planned::row).toList();
        int changed = (int) all.stream().filter(Row::changed).count();
        int problems = (int) all.stream().filter(row -> row.problem() != null).count();
        return new CodeChangePreviewDTO(category.getId(), category.getName(), all.size(), changed, problems,
                page, size, totalRows, totalPages, rows);
    }

    private static boolean contains(String value, String lowerText) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerText);
    }
}
