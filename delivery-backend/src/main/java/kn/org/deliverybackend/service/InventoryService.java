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

    List<StockResponseDTO> getAllStockDetails();

    InventorySummaryDTO getSummary();
}
