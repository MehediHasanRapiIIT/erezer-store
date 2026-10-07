package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.variant.BulkFitsRequestDTO;
import kn.org.deliverybackend.dto.variant.ProductFitsRequestDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.enumeration.Fit;
import kn.org.deliverybackend.enumeration.StockScope;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.CategoryTree;
import kn.org.deliverybackend.service.VariantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * The same fits for many products at once: the ones chosen, or a whole category
 * (with its subcategories). Each product is changed exactly as its own Fit
 * section would change it, and prices are left as they are. A product with no
 * sizes can't come in fits — a fit's stock is kept size by size — so it is left
 * alone and counted.
 */
@Service
@RequiredArgsConstructor
public class ProductFitsService {

    private final VariantService variantService;
    private final VariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    /** What a bulk change did, for the message the admin sees. */
    public record Result(int changed, int withoutSizes, String message) {
    }

    @Transactional
    public Result applyToMany(BulkFitsRequestDTO request) {
        List<Fit> fits = new ArrayList<>();
        for (String raw : request.getFits()) {
            Fit fit = Fit.parse(raw);
            if (fit != null && !fits.contains(fit)) fits.add(fit);
        }

        List<Product> products;
        String where;
        if (request.getScope() == StockScope.CATEGORY) {
            Long categoryId = request.getCategoryId();
            if (categoryId == null) throw new InvalidRequestException("Choose a category.");
            Category category = categoryRepository.findById(categoryId)
                    .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
            products = productRepository.findLiveByCategories(CategoryTree.family(categoryRepository, categoryId));
            where = category.getName();
        } else if (request.getScope() == StockScope.PRODUCTS) {
            if (request.getProductIds() == null || request.getProductIds().isEmpty()) {
                throw new InvalidRequestException("Choose at least one product.");
            }
            products = productRepository.findAllById(request.getProductIds()).stream()
                    .filter(p -> !Boolean.TRUE.equals(p.getDeleted())).toList();
            where = "the products you chose";
        } else {
            throw new InvalidRequestException("Choose products or a category.");
        }
        if (products.isEmpty()) throw new InvalidRequestException("There are no products to change.");

        ProductFitsRequestDTO each = new ProductFitsRequestDTO(fits.stream()
                .map(f -> new ProductFitsRequestDTO.Choice(f.name(), null, false)).toList());
        int changed = 0;
        int withoutSizes = 0;
        for (Product product : products) {
            if (variantRepository.findByProductId(product.getId()).isEmpty()) {
                withoutSizes++;
                continue;
            }
            variantService.setFits(product.getId(), each);
            changed++;
        }

        String fitWords = fits.isEmpty() ? "no fit"
                : String.join(" and ", fits.stream().map(Fit::label).toList());
        String message = changed + (changed == 1 ? " product in " : " products in ") + where
                + (changed == 1 ? " now comes in " : " now come in ") + fitWords + "."
                + (withoutSizes > 0 ? " " + withoutSizes + " with no sizes "
                    + (withoutSizes == 1 ? "was" : "were") + " left as " + (withoutSizes == 1 ? "it was." : "they were.") : "");
        return new Result(changed, withoutSizes, message);
    }
}
