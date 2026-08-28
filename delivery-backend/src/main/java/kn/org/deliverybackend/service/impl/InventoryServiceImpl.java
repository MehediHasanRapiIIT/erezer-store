package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.product.AdminStockUpdateRequestDTO;
import kn.org.deliverybackend.dto.response.product.InventorySummaryDTO;
import kn.org.deliverybackend.dto.response.product.StockResponseDTO;
import kn.org.deliverybackend.entity.Inventory;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.enumeration.StockOperation;
import kn.org.deliverybackend.enumeration.StockStatus;
import kn.org.deliverybackend.event.StockUpdateEvent;
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
                    product.getLowStockThreshold()
            );
        }
        return toStockResponseDTO(product, inventory);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockResponseDTO> getAllStockDetails() {
        return productRepository.findAll().stream()
                .map(product -> {
                    // Use findByProductId (read-only) — don't auto-create in a read-only tx
                    Inventory inventory = inventoryRepository.findByProductId(product.getId())
                            .orElse(null);
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
                                product.getLowStockThreshold()
                        );
                    }
                    return toStockResponseDTO(product, inventory);
                })
                .collect(Collectors.toList());
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

        int current = inventory.getStockQuantity();
        int qty = request.getQuantity();
        StockOperation operation = request.getOperation();

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
        Inventory inventory = inventoryRepository.findByProductIdWithLock(product.getId())
                .orElseGet(() -> createInventoryForProduct(product));
        int newQty = inventory.getStockQuantity() - quantity;
        inventory.setStockQuantity(newQty);
        inventoryRepository.save(inventory);
        product.setStockQuantity(newQty);
        productRepository.save(product);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private StockResponseDTO toStockResponseDTO(Product product, Inventory inventory) {
        StockStatus status = computeStatusFromQty(inventory.getStockQuantity(), inventory.getLowStockThreshold());
        return new StockResponseDTO(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getImageUrl(),
                product.getUnit() != null ? product.getUnit() : (inventory.getUnit() != null ? inventory.getUnit() : "units"),
                inventory.getStockQuantity(),
                status,
                inventory.getLowStockThreshold()
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
