package com.order.processing.service.repository;

import com.order.processing.service.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, String> {

    @Modifying
    @Query(value = "UPDATE products SET quantity = quantity - ?2 WHERE product_id = ?1 AND quantity >= ?2", nativeQuery = true)
    int updateQuantityIfExists(String productId, int quantity);

    @Modifying
    @Query(value = "UPDATE products SET quantity = quantity + ?2 WHERE product_id = ?1", nativeQuery = true)
    int incrementQuantity(String productId, int quantity);
}
