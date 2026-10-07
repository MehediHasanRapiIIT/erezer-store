package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.product.AdminStockUpdateRequestDTO;
import kn.org.deliverybackend.dto.response.product.InventorySummaryDTO;
import kn.org.deliverybackend.dto.response.product.StockResponseDTO;
import kn.org.deliverybackend.entity.Inventory;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.enumeration.StockOperation;
import kn.org.deliverybackend.enumeration.StockStatus;
import kn.org.deliverybackend.event.StockUpdateEvent;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.InventoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final kn.org.deliverybackend.repository.CategoryRepository categoryRepository;
    private final kn.org.deliverybackend.repository.VariantRepository variantRepository;

    // -------------------------------------------------------------------------
    // Status computation
    // -------------------------------------------------------------------------

    @Override
    public StockStatus computeStatus(Product product) {
        int qty = inventoryRepository.findByProductId(product.getId())
                .map(Inventory::getStockQuantity)
                .orElse(product.getStockQuantity());
        return computeStatusFromQty(qty, product.getLowStockThreshold());
    }

    private StockStatus computeStatusFromQty(int qty, Integer threshold) {
        if (qty == 0) return StockStatus.OUT_OF_STOCK;
        if (threshold != null && qty <= threshold) return StockStatus.LOW_STOCK;
        return StockStatus.IN_STOCK;
    }

    // -------------------------------------------------------------------------
    // Read stock
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public StockResponseDTO getStockStatus(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        Inventory inventory = inventoryRepository.findByProductId(productId).orElse(null);
        if (inventory == null) {
            StockStatus status = computeStatusFromQty(product.getStockQuantity(), product.getLowStockThreshold());
            return new StockResponseDTO(
                    product.getId(),
                    product.getName(),
                    product.getSku(),
                    product.getImageUrl(),
                    product.getUnit() != null ? product.getUnit() : "units",
                    product.getStockQuantity(),
                    status,
                    product.getLowStockThreshold(),
                    product.getProductCode()
            );
        }
        return toStockResponseDTO(product, inventory);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockResponseDTO> getAllStockDetails() {
        return productRepository.findAll().stream()
                .map(this::toStockRow)
                .collect(Collectors.toList());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.data.domain.Page<StockResponseDTO> stockPage(String q, int page, int size) {
        return productRepository.searchForInventory(kn.org.deliverybackend.util.SearchText.likePattern(q),
                        org.springframework.data.domain.PageRequest.of(Math.max(page, 0),
                                kn.org.deliverybackend.util.SearchText.pageSize(size)))
                .map(this::toStockRow);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.data.domain.Page<StockResponseDTO> lowStock(String q, int page, int size) {
        // The database picks the same rows the stock status marks LOW_STOCK or OUT_OF_STOCK.
        return productRepository.findLowStock(kn.org.deliverybackend.util.SearchText.likePattern(q),
                        org.springframework.data.domain.PageRequest.of(Math.max(page, 0),
                                kn.org.deliverybackend.util.SearchText.pageSize(size)))
                .map(this::toStockRow);
    }

    /** One product's stock row: from its inventory record, or its cached stock when it has none yet. */
    private StockResponseDTO toStockRow(Product product) {
        // Use findByProductId (read-only) — don't auto-create in a read-only tx
        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElse(null);
        if (inventory == null) {
            // Product has no inventory row yet — show product's cached stock value
            StockStatus status = computeStatusFromQty(product.getStockQuantity(), product.getLowStockThreshold());
            return new StockResponseDTO(
                    product.getId(),
                    product.getName(),
                    product.getSku(),
                    product.getImageUrl(),
                    product.getUnit() != null ? product.getUnit() : "units",
                    product.getStockQuantity(),
                    status,
                    product.getLowStockThreshold(),
                    product.getProductCode()
            );
        }
        return toStockResponseDTO(product, inventory);
    }

    @Override
    @Transactional(readOnly = true)
    public InventorySummaryDTO getSummary() {
        List<StockResponseDTO> all = getAllStockDetails();
        int criticalLow = (int) all.stream()
                .filter(s -> s.getStockStatus() == StockStatus.LOW_STOCK)
                .count();
        int outOfStock = (int) all.stream()
                .filter(s -> s.getStockStatus() == StockStatus.OUT_OF_STOCK)
                .count();
        int reorderPending = criticalLow + outOfStock;
        return new InventorySummaryDTO(criticalLow, outOfStock, reorderPending);
    }

    // -------------------------------------------------------------------------
    // Lock for order placement
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public Product lockAndGetProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        getOrCreateInventory(product);
        inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + productId));
        return product;
    }

    // -------------------------------------------------------------------------
    // Admin stock update
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public StockResponseDTO updateStock(Long productId, AdminStockUpdateRequestDTO request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseGet(() -> createInventoryForProduct(product));

        int qty = request.getQuantity();
        StockOperation operation = request.getOperation();

        // Sold in sizes: the figure is per size, and the product's own stock
        // is their total.
        List<Variant> sizes = variantRepository.findByProductId(productId);
        if (!sizes.isEmpty()) {
            if (operation != StockOperation.SET && qty <= 0) {
                throw new InvalidStockOperationException(
                        "Quantity must be greater than 0 for " + operation + " operations");
            }
            changeSizes(sizes, operation, qty);
            applyUnitAndThreshold(product, inventory, request.getUnit(), request.getLowStockThreshold());
            followSizes(productId);
            return toStockResponseDTO(product, inventory);
        }

        int current = inventory.getStockQuantity();

        if (operation == StockOperation.INCREMENT || operation == StockOperation.DECREMENT) {
            if (qty <= 0) {
                throw new InvalidStockOperationException(
                        "Quantity must be greater than 0 for " + operation + " operations");
            }
        }

        int newQty;
        switch (operation) {
            case SET:       newQty = qty; break;
            case INCREMENT: newQty = current + qty; break;
            case DECREMENT:
                if (qty > current) {
                    throw new InvalidStockOperationException(
                            String.format("Cannot decrement by %d: only %d units available", qty, current));
                }
                newQty = current - qty;
                break;
            default:
                throw new InvalidStockOperationException("Unknown operation: " + operation);
        }

        inventory.setStockQuantity(newQty);
        if (request.getUnit() != null && !request.getUnit().isBlank()) {
            inventory.setUnit(request.getUnit());
            product.setUnit(request.getUnit());
        }
        if (request.getLowStockThreshold() != null) {
            inventory.setLowStockThreshold(request.getLowStockThreshold());
            product.setLowStockThreshold(request.getLowStockThreshold());
        }
        inventoryRepository.save(inventory);

        product.setStockQuantity(newQty);
        productRepository.save(product);

        StockStatus newStatus = computeStatusFromQty(newQty, inventory.getLowStockThreshold());
        eventPublisher.publishEvent(new StockUpdateEvent(this, product.getId(), newQty, newStatus));

        return toStockResponseDTO(product, inventory);
    }

    @Override
    @Transactional
    public StockResponseDTO setSizeStock(Long productId,
            kn.org.deliverybackend.dto.request.product.SizeStockUpdateRequestDTO request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseGet(() -> createInventoryForProduct(product));

        java.util.Map<Long, Variant> mine = new java.util.HashMap<>();
        for (Variant v : variantRepository.findByProductId(productId)) {
            mine.put(v.getId(), v);
        }
        // Every size is checked before the first is changed.
        for (var item : request.getSizes()) {
            if (!mine.containsKey(item.getVariantId())) {
                throw new InvalidRequestException("That size isn't one of this product's sizes.");
            }
        }
        for (var item : request.getSizes()) {
            Variant v = mine.get(item.getVariantId());
            v.setStockQuantity(item.getQuantity());
            variantRepository.save(v);
        }
        variantRepository.flush();
        applyUnitAndThreshold(product, inventory, request.getUnit(), request.getLowStockThreshold());
        followSizes(productId);
        return toStockResponseDTO(product, inventory);
    }

    /** One change on each of these sizes; taking off more than a size has leaves it at 0. Returns how many hit 0 that way. */
    private int changeSizes(List<Variant> sizes, StockOperation operation, int quantity) {
        int emptied = 0;
        for (Variant v : sizes) {
            int current = v.getStockQuantity() == null ? 0 : v.getStockQuantity();
            int next = switch (operation) {
                case SET -> quantity;
                case INCREMENT -> current + quantity;
                case DECREMENT -> Math.max(current - quantity, 0);
            };
            if (operation == StockOperation.DECREMENT && quantity > current) emptied++;
            v.setStockQuantity(next);
            variantRepository.save(v);
        }
        variantRepository.flush();
        return emptied;
    }

    private void applyUnitAndThreshold(Product product, Inventory inventory, String unit, Integer threshold) {
        if (unit != null && !unit.isBlank()) {
            inventory.setUnit(unit);
            product.setUnit(unit);
        }
        if (threshold != null) {
            inventory.setLowStockThreshold(threshold);
            product.setLowStockThreshold(threshold);
        }
        inventoryRepository.save(inventory);
        productRepository.save(product);
    }

    @Override
    @Transactional
    public List<StockResponseDTO> setStockForEach(
            kn.org.deliverybackend.dto.request.product.BulkStockUpdateRequestDTO request) {
        List<StockResponseDTO> results = new java.util.ArrayList<>();
        for (var item : request.getUpdates()) {
            if (item.getSizes() != null && !item.getSizes().isEmpty()) {
                var bySize = new kn.org.deliverybackend.dto.request.product.SizeStockUpdateRequestDTO();
                bySize.setSizes(item.getSizes());
                bySize.setUnit(item.getUnit());
                bySize.setLowStockThreshold(item.getLowStockThreshold());
                results.add(setSizeStock(item.getProductId(), bySize));
                continue;
            }
            AdminStockUpdateRequestDTO one = new AdminStockUpdateRequestDTO();
            one.setOperation(StockOperation.SET);
            one.setQuantity(item.getQuantity());
            one.setUnit(item.getUnit());
            one.setLowStockThreshold(item.getLowStockThreshold());
            results.add(updateStock(item.getProductId(), one));
        }
        return results;
    }

    @Override
    @Transactional
    public kn.org.deliverybackend.dto.response.product.BulkStockResultDTO adjustStock(
            kn.org.deliverybackend.dto.request.product.BulkStockAdjustRequestDTO request) {
        StockOperation operation = request.getOperation();
        int quantity = request.getQuantity();
        if (operation != StockOperation.SET && quantity <= 0) {
            throw new InvalidRequestException("Enter how many units to add or remove.");
        }

        List<Product> products;
        String scopeLabel;
        switch (request.getScope()) {
            case PRODUCTS -> {
                List<Long> ids = request.getProductIds();
                if (ids == null || ids.isEmpty()) {
                    throw new InvalidRequestException("Choose at least one product.");
                }
                products = productRepository.findAllById(ids).stream()
                        .filter(p -> !Boolean.TRUE.equals(p.getDeleted())).toList();
                scopeLabel = "the products you chose";
            }
            case CATEGORY -> {
                Long categoryId = request.getCategoryId();
                if (categoryId == null) {
                    throw new InvalidRequestException("Choose a category.");
                }
                var category = categoryRepository.findById(categoryId)
                        .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
                // A main category covers its subcategories' products too.
                products = productRepository.findLiveByCategories(
                        kn.org.deliverybackend.service.CategoryTree.family(categoryRepository, categoryId));
                scopeLabel = category.getName();
            }
            default -> {
                products = productRepository.findAll().stream()
                        .filter(p -> !Boolean.TRUE.equals(p.getDeleted())).toList();
                scopeLabel = "the whole shop";
            }
        }
        if (products.isEmpty()) {
            throw new InvalidRequestException("There are no products to update.");
        }

        // Sizes named for this change, e.g. only M and L. None named means every size.
        java.util.Set<String> onlySizes = new java.util.HashSet<>();
        if (request.getSizes() != null) {
            for (String s : request.getSizes()) {
                if (s != null && !s.isBlank()) onlySizes.add(s.trim().toUpperCase());
            }
        }

        // Fits named for this change, e.g. only Regular Fit. None named means every fit.
        java.util.Set<String> onlyFits = new java.util.HashSet<>();
        if (request.getFits() != null) {
            for (String f : request.getFits()) {
                kn.org.deliverybackend.enumeration.Fit fit = kn.org.deliverybackend.enumeration.Fit.parse(f);
                if (fit != null) onlyFits.add(fit.name());
            }
        }

        int setToZero = 0;
        int inSizes = 0;
        int sizesChanged = 0;
        int leftAlone = 0;
        for (Product product : products) {
            List<Variant> sizes = variantRepository.findByProductId(product.getId());
            if (!sizes.isEmpty()) {
                List<Variant> chosen = sizes.stream()
                        .filter(v -> onlySizes.isEmpty()
                                || (v.getSize() != null && onlySizes.contains(v.getSize().trim().toUpperCase())))
                        .filter(v -> onlyFits.isEmpty() || (v.getFit() != null && onlyFits.contains(v.getFit())))
                        .toList();
                if (chosen.isEmpty()) {
                    leftAlone++;
                    continue;
                }
                if (changeSizes(chosen, operation, quantity) > 0) setToZero++;
                followSizes(product.getId());
                inSizes++;
                sizesChanged += chosen.size();
                continue;
            }
            if (!onlySizes.isEmpty() || !onlyFits.isEmpty()) {
                // Not sold in sizes, and the change is for named sizes or fits only.
                leftAlone++;
                continue;
            }
            Inventory inventory = inventoryRepository.findByProductIdWithLock(product.getId())
                    .orElseGet(() -> createInventoryForProduct(product));
            int current = inventory.getStockQuantity();
            int newQty = switch (operation) {
                case SET -> quantity;
                case INCREMENT -> current + quantity;
                // Asked to remove more than there is: that product lands on 0.
                case DECREMENT -> Math.max(current - quantity, 0);
            };
            if (operation == StockOperation.DECREMENT && quantity > current) {
                setToZero++;
            }
            inventory.setStockQuantity(newQty);
            inventoryRepository.save(inventory);
            product.setStockQuantity(newQty);
            productRepository.save(product);
            eventPublisher.publishEvent(new StockUpdateEvent(this, product.getId(), newQty,
                    computeStatusFromQty(newQty, inventory.getLowStockThreshold())));
        }

        String what = switch (operation) {
            case SET -> "Set stock to " + quantity + " for ";
            case INCREMENT -> "Added " + quantity + " to ";
            case DECREMENT -> "Removed " + quantity + " from ";
        };
        int changed = products.size() - leftAlone;
        if (changed == 0) {
            throw new InvalidRequestException(onlyFits.isEmpty()
                    ? "None of those products has " + (onlySizes.size() == 1 ? "that size." : "any of those sizes.")
                    : "None of those products comes in that fit and size.");
        }
        String message = what + changed + " product" + (changed == 1 ? "" : "s")
                + " in " + scopeLabel + "."
                + (inSizes > 0 ? " For the " + inSizes + " sold in sizes that is per size ("
                    + sizesChanged + " size" + (sizesChanged == 1 ? "" : "s") + " changed)." : "")
                + (leftAlone > 0 ? " " + leftAlone + " without "
                    + (!onlyFits.isEmpty() ? "that fit or size" : onlySizes.size() == 1 ? "that size" : "those sizes")
                    + (leftAlone == 1 ? " was" : " were") + " left as " + (leftAlone == 1 ? "it was." : "they were.") : "")
                + (setToZero > 0 ? " " + setToZero + " had less than that and " + (setToZero == 1 ? "is" : "are") + " now 0." : "");

        return kn.org.deliverybackend.dto.response.product.BulkStockResultDTO.builder()
                .updated(changed)
                .setToZero(setToZero)
                .scopeLabel(scopeLabel)
                .message(message)
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers used by OrderServiceImpl
    // -------------------------------------------------------------------------

    @Override
    public int getAvailableStock(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .map(Inventory::getStockQuantity)
                .orElse(0);
    }

    @Override
    @Transactional
    public void decrementStock(Product product, int quantity) {
        adjustStock(product, -quantity);
    }

    @Override
    @Transactional
    public void incrementStock(Product product, int quantity) {
        adjustStock(product, quantity);
    }

    /**
     * A product sold in sizes has stock in two places: each size's, which is
     * what an order takes from, and the product's own, which is what the shop
     * and the Inventory page show. The second is kept as the total of the
     * first, so a product given 20 of each size is never shown as out of stock,
     * and one whose sizes have all sold is never shown as in stock.
     */
    @Override
    @Transactional
    public void followSizes(Long productId) {
        if (variantRepository.findByProductId(productId).isEmpty()) return;
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) return;
        int total = (int) Math.min(Integer.MAX_VALUE, variantRepository.sumStockByProduct(productId));
        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseGet(() -> createInventoryForProduct(product));
        if (inventory.getStockQuantity() == total && product.getStockQuantity() == total) return;
        inventory.setStockQuantity(total);
        inventoryRepository.save(inventory);
        product.setStockQuantity(total);
        productRepository.save(product);
        eventPublisher.publishEvent(new StockUpdateEvent(this, productId, total,
                computeStatusFromQty(total, inventory.getLowStockThreshold())));
    }

    /**
     * Single write path for both directions, so the inventory row and the
     * denormalised product.stockQuantity can never disagree.
     *
     * <p>Takes the row lock first: cancellation and a concurrent order for the
     * same product would otherwise read the same figure and one write would be
     * lost.
     */
    private void adjustStock(Product product, int delta) {
        Inventory inventory = inventoryRepository.findByProductIdWithLock(product.getId())
                .orElseGet(() -> createInventoryForProduct(product));
        int current = inventory.getStockQuantity();
        // Never let a credit-back push the figure negative through bad data.
        int newQty = Math.max(0, current + delta);
        inventory.setStockQuantity(newQty);
        inventoryRepository.save(inventory);
        product.setStockQuantity(newQty);
        productRepository.save(product);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** The product's sizes with the stock of each, in the order customers see them. */
    private List<StockResponseDTO.SizeStock> sizesOf(Long productId) {
        return variantRepository.findByProductId(productId).stream()
                .sorted(java.util.Comparator
                        .comparingInt((Variant v) -> kn.org.deliverybackend.enumeration.Fit.rank(v.getFit()))
                        .thenComparingInt(InventoryServiceImpl::sizeRank))
                .map(v -> new StockResponseDTO.SizeStock(v.getId(), v.getSize(),
                        v.getStockQuantity() == null ? 0 : v.getStockQuantity(),
                        v.getFit(), kn.org.deliverybackend.enumeration.Fit.labelOf(v.getFit())))
                .toList();
    }

    private static final List<String> SIZE_ORDER = List.of("XS", "S", "M", "L", "XL", "XXL", "XXXL");

    /** S, M, L rather than the alphabet's L, M, S; anything else keeps its place after them. */
    private static int sizeRank(Variant v) {
        int i = v.getSize() == null ? -1 : SIZE_ORDER.indexOf(v.getSize().trim().toUpperCase());
        return i < 0 ? SIZE_ORDER.size() : i;
    }

    private StockResponseDTO toStockResponseDTO(Product product, Inventory inventory) {
        StockResponseDTO dto = stockRowOf(product, inventory);
        dto.setSizes(sizesOf(product.getId()));
        return dto;
    }

    private StockResponseDTO stockRowOf(Product product, Inventory inventory) {
        StockStatus status = computeStatusFromQty(inventory.getStockQuantity(), inventory.getLowStockThreshold());
        return new StockResponseDTO(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getImageUrl(),
                product.getUnit() != null ? product.getUnit() : (inventory.getUnit() != null ? inventory.getUnit() : "units"),
                inventory.getStockQuantity(),
                status,
                inventory.getLowStockThreshold(),
                product.getProductCode()
        );
    }

    private Inventory getOrCreateInventory(Product product) {
        return inventoryRepository.findByProductId(product.getId())
                .orElseGet(() -> createInventoryForProduct(product));
    }

    private Inventory createInventoryForProduct(Product product) {
        Inventory inventory = new Inventory();
        inventory.setProductId(product.getId());
        inventory.setStockQuantity(product.getStockQuantity());
        inventory.setLowStockThreshold(product.getLowStockThreshold());
        inventory.setUnit(product.getUnit());
        return inventoryRepository.save(inventory);
    }
}
