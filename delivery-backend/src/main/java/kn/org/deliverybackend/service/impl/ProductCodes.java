package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suggests product codes: the category's initials and the next free number,
 * e.g. Erezer Pink → EP-1003 when EP-1002 is the highest so far.
 *
 * <p>Only a suggestion: staff can still type any code, and codes may be shared
 * (PRODUCT-CODE-PLAN.md). A number is never offered again once an order has
 * carried it, even if its product has since been deleted, because old invoices
 * still show it.
 */
@Service
@RequiredArgsConstructor
public class ProductCodes {

    /** Where numbering starts for a category that has no codes in this style yet. */
    static final int FIRST_NUMBER = 1001;
    static final int MAX_AT_ONCE = 50;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    /** The next {@code count} codes for a category, in order. */
    @Transactional(readOnly = true)
    public List<String> next(Long categoryId, int count) {
        if (count < 1 || count > MAX_AT_ONCE) {
            throw new InvalidRequestException("Ask for between 1 and " + MAX_AT_ONCE + " codes.");
        }
        Category category = categoryRepository.findById(categoryId)
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
        String prefix = prefixFor(category.getName());
        int start = nextNumber(prefix, productRepository.findCodesStartingWith(prefix));

        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            codes.add(prefix + "-" + (start + i));
        }
        return codes;
    }

    /**
     * Two letters from a category's name: the first letters of its first two
     * words (Erezer Pink → EP, T-Shirts → TS), or the first two letters of a
     * single word (Caps → CA). PR when the name has no English letters to use.
     */
    static String prefixFor(String categoryName) {
        List<String> words = Arrays.stream((categoryName == null ? "" : categoryName).split("[^A-Za-z]+"))
                .filter(w -> !w.isEmpty())
                .toList();
        String prefix;
        if (words.size() >= 2) {
            prefix = "" + words.get(0).charAt(0) + words.get(1).charAt(0);
        } else if (words.size() == 1 && words.get(0).length() >= 2) {
            prefix = words.get(0).substring(0, 2);
        } else {
            prefix = "PR";
        }
        return prefix.toUpperCase(Locale.ROOT);
    }

    /**
     * One past the highest number already used with this prefix. Only codes in
     * exactly this style count — "EP-1002" does, "EP-1002-B" and "EPX-9" don't.
     */
    static int nextNumber(String prefix, List<String> existing) {
        Pattern style = Pattern.compile("^" + Pattern.quote(prefix) + "-(\\d{1,9})$", Pattern.CASE_INSENSITIVE);
        int highest = FIRST_NUMBER - 1;
        for (String code : existing) {
            if (code == null) continue;
            Matcher m = style.matcher(code.trim());
            if (m.matches()) {
                highest = Math.max(highest, Integer.parseInt(m.group(1)));
            }
        }
        return highest + 1;
    }
}
