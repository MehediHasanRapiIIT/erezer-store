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

    Product lockAndGetProduct(Long productId);

    int getAvailableStock(Long productId);

    void decrementStock(Product product, int quantity);

    /** Credits units back, e.g. when an order that reserved them is cancelled. */
    void incrementStock(Product product, int quantity);

    List<StockResponseDTO> getAllStockDetails();

    /** The Inventory page: one page of stock rows, optionally searched by product name or SKU. */
    org.springframework.data.domain.Page<StockResponseDTO> stockPage(String q, int page, int size);

    /** Every product that is low on stock or out of stock, for the restock alerts. */
    List<StockResponseDTO> lowStock();

    InventorySummaryDTO getSummary();
}
