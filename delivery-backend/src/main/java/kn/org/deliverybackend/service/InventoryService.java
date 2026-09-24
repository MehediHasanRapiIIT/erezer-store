package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.request.product.AdminStockUpdateRequestDTO;
import kn.org.deliverybackend.dto.response.product.InventorySummaryDTO;
import kn.org.deliverybackend.dto.response.product.StockResponseDTO;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.enumeration.StockStatus;

import java.util.List;

public interface InventoryService {

    StockStatus computeStatus(Product product);

    StockResponseDTO getStockStatus(Long productId);

    StockResponseDTO updateStock(Long productId, AdminStockUpdateRequestDTO request);

    /**
     * One stock change for many products at once: the ones chosen on the page,
     * a whole category, or every product. Removing more than a product has
     * leaves it at 0 rather than failing the whole run.
     */
    kn.org.deliverybackend.dto.response.product.BulkStockResultDTO adjustStock(
            kn.org.deliverybackend.dto.request.product.BulkStockAdjustRequestDTO request);

    /** Sets an exact stock figure for each product listed. */
    List<StockResponseDTO> setStockForEach(kn.org.deliverybackend.dto.request.product.BulkStockUpdateRequestDTO request);

    Product lockAndGetProduct(Long productId);

    int getAvailableStock(Long productId);

    void decrementStock(Product product, int quantity);

    /** Credits units back, e.g. when an order that reserved them is cancelled. */
    void incrementStock(Product product, int quantity);

    List<StockResponseDTO> getAllStockDetails();

    /** The Inventory page: one page of stock rows, optionally searched by product name or SKU. */
    org.springframework.data.domain.Page<StockResponseDTO> stockPage(String q, int page, int size);

    /** Restock alerts: one page of products low on stock or out of stock, optionally searched by name or SKU. */
    org.springframework.data.domain.Page<StockResponseDTO> lowStock(String q, int page, int size);

    InventorySummaryDTO getSummary();
}
